
package pacote;

import java.io.ObjectOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class DesktopEnvioThread implements Runnable {

    private volatile boolean paradaManual = false;

    private volatile ServerSocket emissor;

    private volatile ExecutorService atendimentos;

    private final Set<Socket> clientesAtivos =
            ConcurrentHashMap.newKeySet();

    @Override
    public void run() {

        if (paradaManual) {
            return;
        }

        ExecutorService executor =
                Executors.newVirtualThreadPerTaskExecutor();

        atendimentos = executor;

        try (ServerSocket servidor = new ServerSocket()) {

            emissor = servidor;

            servidor.setReuseAddress(true);

            servidor.bind(
                    new InetSocketAddress(
                            "0.0.0.0",
                            Util.PortaEnvioDesktop
                    )
            );

            System.out.println(
                    "Servidor Desktop de consulta ativo na porta "
                    + Util.PortaEnvioDesktop
            );

            while (!paradaManual) {

                Socket cliente = servidor.accept();

                cliente.setSoTimeout(15000);

                clientesAtivos.add(cliente);

                try {

                    executor.execute(
                            () -> atenderCliente(cliente)
                    );

                } catch (RejectedExecutionException e) {

                    clientesAtivos.remove(cliente);

                    fecharSocket(cliente);
                }
            }

        } catch (SocketException e) {

            if (!paradaManual) {

                Util.registrarErroServidor(
                        "desktop",
                        "INTERNO/NÃO APLICÁVEL",
                        "NÃO APLICÁVEL",
                        "DesktopEnvioThread.run",
                        e
                );
            }

        } catch (IOException e) {

            if (!paradaManual) {

                Util.registrarErroServidor(
                        "desktop",
                        "INTERNO/NÃO APLICÁVEL",
                        "NÃO APLICÁVEL",
                        "DesktopEnvioThread.run",
                        e
                );
            }

        } finally {

            emissor = null;

            fecharClientes();

            executor.shutdownNow();

            atendimentos = null;

            System.out.println(
                    "Servidor Desktop de consulta encerrado."
            );
        }
    }

    /**
     * Atende individualmente uma consulta de historico.
     */
    private void atenderCliente(Socket cliente) {

        String ip = cliente.getInetAddress()
                .getHostAddress();

        try (Socket socket = cliente) {

            // Consulta diretamente a memoria compartilhada.
            String historico = Util.obterHistoricoHtml();

            // Mantem compatibilidade com o protocolo original.
            ObjectOutputStream output =
                    new ObjectOutputStream(
                            socket.getOutputStream()
                    );

            output.writeUTF(historico);
            output.flush();

        } catch (IOException e) {

            Util.registrarErroServidor(
                    "desktop",
                    "desktop",
                    ip,
                    "DesktopEnvioThread.atenderCliente",
                    e
            );

        } finally {

            clientesAtivos.remove(cliente);
        }
    }

    /**
     * Fecha o servidor e as conexoes ativas.
     */
    public void fecharServidor() {

        paradaManual = true;

        try {

            if (emissor != null && !emissor.isClosed()) {
                emissor.close();
            }

        } catch (IOException e) {

            System.err.println(
                    "Erro ao fechar servidor de consulta: "
                    + e.getMessage()
            );
        }

        fecharClientes();

        ExecutorService executor = atendimentos;

        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void fecharClientes() {

        for (Socket cliente : clientesAtivos) {
            fecharSocket(cliente);
        }

        clientesAtivos.clear();
    }

    private void fecharSocket(Socket socket) {

        try {

            if (socket != null && !socket.isClosed()) {
                socket.close();
            }

        } catch (IOException ignored) {
        }
    }
}
