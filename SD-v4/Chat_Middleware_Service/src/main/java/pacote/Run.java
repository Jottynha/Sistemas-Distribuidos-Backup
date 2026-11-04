
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

        boolean modoHeadless = Arrays.asList(args)
                .contains("--headless");

        if (modoHeadless) {

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
     * Inicia os tres servicos sem abrir o painel.
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
        try {

            new CountDownLatch(1).await();

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            Util.pararTodosServicos();
        }
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
