package pacote;

import pacote.replication.ReplicacaoConfig;
import pacote.replication.ReplicacaoClient;
import pacote.replication.ReplicacaoHeartbeatMonitor;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import javax.swing.SwingUtilities;

public class Run {
    public static void main(String[] args) {
        try {
            Util.inicializarRepositorio();
        } catch (IOException erro) {
            System.err.println("Erro ao inicializar o repositorio: " + erro.getMessage());
            erro.printStackTrace();
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(Util::pararTodosServicos, "middleware-shutdown"));

        boolean modoReplica = Arrays.asList(args).contains("--replica-only");
        boolean modoHeadless = Arrays.asList(args).contains("--headless");
        if (modoReplica) iniciarComoReserva();
        else if (modoHeadless) iniciarSemInterface();
        else iniciarPainel();
    }

    private static void iniciarPainel() {
        SwingUtilities.invokeLater(() -> {
            FrmPainelDeControle painel = new FrmPainelDeControle();
            painel.setLocationRelativeTo(null);
            painel.setVisible(true);
        });
    }

    private static void iniciarComoReserva() {
        try {
            Util.iniciarRecepcaoReplicacao();
            if (Util.atendimentoAtivo()) {
                System.out.println("Middleware B iniciou como ACTIVE (marcador persistente de promocao encontrado).");
            } else {
                System.out.println("Middleware B em STANDBY. A promocao sera automatica se os heartbeats do principal expirarem.");
            }
            aguardarEncerramento();
        } catch (Exception erro) {
            registrarFalha("internal", erro);
            Util.pararTodosServicos();
        }
    }

    private static void iniciarSemInterface() {
        System.out.println("Iniciando Middleware A em modo sem interface...");
        if (ReplicacaoConfig.habilitada()) {
            try {
                var config = ReplicacaoConfig.carregarCliente();
                // O monitor e iniciado antes da primeira consulta. Se o reserva
                // ainda nao estiver disponivel, o principal permanece em fail-closed
                // e volta a tentar automaticamente quando a rede se recuperar.
                ReplicacaoHeartbeatMonitor monitor = ReplicacaoHeartbeatMonitor.iniciar(config);
                try {
                    String estado = ReplicacaoClient.consultarEstadoReserva(config);
                    if ("ACTIVE".equals(estado)) {
                        monitor.marcarStandby();
                        System.out.println("O Middleware B ja esta ACTIVE. Este processo permanecera em standby.");
                    } else {
                        monitor.assumirAtendimentoInicial();
                        System.out.println("Reserva confirmou STANDBY. Principal autorizado a iniciar atendimento.");
                    }
                } catch (Exception erroInicial) {
                    monitor.marcarStandby();
                    registrarFalha("replicacao", erroInicial);
                    System.err.println("Fail-closed: reserva ainda nao respondeu. O principal aguardara e tentara novamente via heartbeat.");
                }
            } catch (Exception erro) {
                registrarFalha("replicacao", erro);
                System.err.println("Fail-closed: configuracao de replicacao invalida; nenhum servico de cliente sera iniciado.");
            }
            System.out.println("Monitor de failover ativo. Pressione CTRL+C para encerrar.");
            aguardarEncerramento();
            return;
        }

        try {
            Util.iniciarServicosDeAtendimento();
            System.out.println("Middleware iniciado. Pressione CTRL+C para encerrar.");
            aguardarEncerramento();
        } catch (Exception erro) {
            registrarFalha("internal", erro);
            Util.pararServicosDeAtendimento();
        }
    }

    private static void aguardarEncerramento() {
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
            Util.pararTodosServicos();
        }
    }

    private static void registrarFalha(String servico, Exception erro) {
        System.err.println("Falha ao iniciar servico " + servico + ": " + erro.getMessage());
        Util.registrarErroServidor(servico, "INTERNO/NÃO APLICÁVEL", "NÃO APLICÁVEL", "Run", erro);
    }
}
