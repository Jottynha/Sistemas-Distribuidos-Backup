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
            ReplicacaoHeartbeatMonitor monitor;
            ReplicacaoConfig config;
            try {
                config = ReplicacaoConfig.carregarCliente();
                monitor = ReplicacaoHeartbeatMonitor.iniciar(config);
            } catch (Exception erro) {
                registrarFalha("replicacao", erro);
                System.err.println("Fail-closed: configuracao de replicacao invalida; nenhum servico de cliente sera iniciado.");
                aguardarEncerramento();
                return;
            }

            boolean atendimentoIniciado = false;
            while (!atendimentoIniciado && !Thread.currentThread().isInterrupted()) {
                try {
                    String estado = ReplicacaoClient.consultarEstadoReserva(config);
                    if ("ACTIVE".equals(estado)) {
                        monitor.marcarStandby();
                        System.out.println("[FAILBACK] Middleware B esta ACTIVE. Solicitando reintegracao controlada do Middleware A.");
                        ReplicacaoClient.reintegrarPrincipal(config);
                    } else {
                        System.out.println("[SINCRONIZACAO] Middleware B esta STANDBY. Verificando e reconciliando os bancos antes do atendimento.");
                        ReplicacaoClient.sincronizarReserva(config);
                    }

                    monitor.assumirAtendimentoInicial();
                    atendimentoIniciado = true;
                    System.out.println("Middleware A autorizado a atender. Pressione CTRL+C para encerrar.");
                } catch (Exception erro) {
                    monitor.marcarStandby();
                    registrarFalha("replicacao", erro);
                    System.err.println("Fail-closed: sincronizacao ou transferencia de lideranca nao concluida. Nova tentativa em 3 segundos.");
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException interrompido) {
                        Thread.currentThread().interrupt();
                    }
                }
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
        Util.registrarErroServidor(servico, "INTERNO/NAO APLICAVEL", "NAO APLICAVEL", "Run", erro);
    }
}
