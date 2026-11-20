
package pacote;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import javax.swing.SwingUtilities;

public class Run {

    public static void main(String[] args) {

        // Inicializa o historico antes dos servicos.
        try {

            Util.inicializarRepositorio();

        } catch (IOException e) {

            System.err.println(
                    "Erro ao inicializar o repositorio: "
                    + e.getMessage()
            );

            e.printStackTrace();

            return;
        }

        // Garante o encerramento dos servicos quando
        // o processo Java for finalizado.
        Runtime.getRuntime().addShutdownHook(
                new Thread(
                        Util::pararTodosServicos,
                        "middleware-shutdown"
                )
        );

        boolean modoReplica = Arrays.asList(args).contains("--replica-only");
        boolean modoHeadless = Arrays.asList(args).contains("--headless");

        if (modoReplica) {
            iniciarComoReserva();
        } else if (modoHeadless) {
            iniciarSemInterface();
        } else {
            iniciarPainel();
        }
    }

    /**
     * Inicia o painel grafico do middleware.
     */
    private static void iniciarPainel() {

        SwingUtilities.invokeLater(() -> {

            FrmPainelDeControle painel =
                    new FrmPainelDeControle();

            painel.setLocationRelativeTo(null);
            painel.setVisible(true);
        });
    }

    /**
     * Inicia somente o receptor interno de replicacao no middleware reserva.
     * O processo utiliza o banco indicado pelas variaveis CHAT_DB_* e nao
     * abre as portas destinadas aos clientes enquanto estiver em standby.
     */
    private static void iniciarComoReserva() {
        try {
            Util.iniciarRecepcaoReplicacao();
            System.out.println(
                    "Middleware reserva em modo de replicacao. Aguardando o middleware principal."
            );
            aguardarEncerramento();
        } catch (Exception erro) {
            registrarFalha("internal", erro);
            Util.pararTodosServicos();
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

    /**
     * Inicia os servicos Desktop, Terceiros (Python), Web e logs sem abrir o painel.
     *
     * Utilizado na execucao fora do NetBeans.
     */
    private static void iniciarSemInterface() {

        System.out.println(
                "Iniciando middleware em modo sem interface..."
        );

        try {

            Util.iniciarDesktop();

            System.out.println(
                    "Servico Desktop iniciado."
            );

        } catch (Exception e) {

            registrarFalha("desktop", e);
        }

        // O modo sem interface tambem precisa disponibilizar o listener
        // TCP/TLS 6665 utilizado pelo cliente Python.
        try {

            Util.iniciarServicoTerceiros();

            System.out.println(
                    "Servico Terceiros (Python) iniciado na porta "
                    + TerceirosJsonRecepcaoThread.PORTA
            );

        } catch (Exception e) {

            registrarFalha("terceiros", e);
        }

        try {

            Util.iniciarServidorWeb();

            System.out.println(
                    "Servico Web iniciado."
            );

        } catch (Exception e) {

            registrarFalha("web", e);
        }

        try {

            Util.iniciarColetorLogs();

            System.out.println(
                    "Coletor de logs iniciado."
            );

        } catch (Exception e) {

            registrarFalha("internal", e);
        }

        System.out.println(
                "Middleware iniciado. Pressione CTRL+C para encerrar."
        );

        // Mantem o processo ativo ate seu encerramento.
        aguardarEncerramento();
    }

    /**
     * Registra falhas na inicializacao dos servicos.
     */
    private static void registrarFalha(
            String servico,
            Exception erro) {

        System.err.println(
                "Falha ao iniciar servico "
                + servico + ": " + erro.getMessage()
        );

        Util.registrarErroServidor(
                servico,
                "INTERNO/NÃO APLICÁVEL",
                "NÃO APLICÁVEL",
                "Run.iniciarSemInterface",
                erro
        );
    }
}
