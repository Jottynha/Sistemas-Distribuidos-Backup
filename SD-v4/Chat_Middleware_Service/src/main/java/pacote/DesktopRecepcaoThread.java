
package pacote;

import java.io.ObjectInputStream;
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

public class DesktopRecepcaoThread implements Runnable {

    private volatile boolean paradaManual = false;

    private volatile ServerSocket receptor;

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

            receptor = servidor;

            servidor.setReuseAddress(true);

            servidor.bind(
                    new InetSocketAddress(
                            "0.0.0.0",
                            Util.PortaRecepcaoDesktop
                    )
            );

            System.out.println(
                    "Servidor Desktop de recebimento ativo na porta "
                    + Util.PortaRecepcaoDesktop
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
                        "DesktopRecepcaoThread.run",
                        e
                );
            }

        } catch (IOException e) {

            if (!paradaManual) {

                Util.registrarErroServidor(
                        "desktop",
                        "INTERNO/NÃO APLICÁVEL",
                        "NÃO APLICÁVEL",
                        "DesktopRecepcaoThread.run",
                        e
                );
            }

        } finally {

            receptor = null;

            fecharClientes();

            executor.shutdownNow();

            atendimentos = null;

            System.out.println(
                    "Servidor Desktop de recebimento encerrado."
            );
        }
    }

    /**
     * Recebe uma mensagem e solicita sua persistencia.
     */
    private void atenderCliente(Socket cliente) {

        String ip = cliente.getInetAddress()
                .getHostAddress();

        try (Socket socket = cliente) {

            ObjectInputStream input =
                    new ObjectInputStream(
                            socket.getInputStream()
                    );

            String mensagem = input.readUTF();

            try {

                // Grava no TXT e atualiza o historico em RAM.
                Util.adicionarMensagem(mensagem);

                // Confirmacao enviada somente apos persistencia.
                enviarConfirmacao(socket, "OK");

                System.out.println(
                        "Mensagem Desktop persistida. IP: " + ip
                );

            } catch (IOException | IllegalArgumentException e) {

                Util.registrarErroServidor(
                        "desktop",
                        "desktop",
                        ip,
                        "DesktopRecepcaoThread.atenderCliente",
                        e
                );

                enviarConfirmacao(
                        socket,
                        "ERROR:Falha ao persistir mensagem"
                );
            }

        } catch (Exception e) {

            if (!paradaManual) {

                Util.registrarErroServidor(
                        "desktop",
                        "desktop",
                        ip,
                        "DesktopRecepcaoThread.atenderCliente",
                        e
                );
            }

        } finally {

            clientesAtivos.remove(cliente);
        }
    }

    /**
     * Envia o ACK ao cliente Desktop.
     */
    private void enviarConfirmacao(
            Socket socket,
            String resposta) {

        try {

            ObjectOutputStream output =
                    new ObjectOutputStream(
                            socket.getOutputStream()
                    );

            output.writeUTF(resposta);
            output.flush();

        } catch (IOException e) {

            /*
             * A gravacao pode ter sido concluida mesmo que
             * o cliente tenha encerrado a conexao antes de
             * receber a confirmacao.
             */
            System.err.println(
                    "Nao foi possivel enviar ACK ao cliente: "
                    + e.getMessage()
            );
        }
    }

    /**
     * Encerra o servidor e suas conexoes.
     */
    public void fecharServidor() {

        paradaManual = true;

        try {

            if (receptor != null && !receptor.isClosed()) {
                receptor.close();
            }

        } catch (IOException e) {

            System.err.println(
                    "Erro ao fechar servidor de recebimento: "
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
