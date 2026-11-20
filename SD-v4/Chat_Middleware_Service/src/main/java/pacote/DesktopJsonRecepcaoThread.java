package pacote;

import protocolo.ChatMessage;
import protocolo.JsonProtocol;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

import java.nio.charset.StandardCharsets;

import java.security.GeneralSecurityException;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class DesktopJsonRecepcaoThread implements Runnable {

    private volatile boolean paradaManual = false;

    private volatile SSLServerSocket receptor;

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

        try {

            SSLContext contexto =
                    ConfiguracaoTLS.criarContextoServidor();

            try (SSLServerSocket servidor =
                    (SSLServerSocket) contexto
                            .getServerSocketFactory()
                            .createServerSocket()) {

                receptor = servidor;

                servidor.setReuseAddress(true);
                servidor.setEnabledProtocols(
                        new String[]{"TLSv1.3", "TLSv1.2"}
                );
                servidor.setNeedClientAuth(false);

                servidor.bind(
                        new InetSocketAddress(
                                "0.0.0.0",
                                Util.PortaRecepcaoJsonTLS
                        )
                );

                System.out.println(
                        "Servidor JSON TLS ativo na porta "
                        + Util.PortaRecepcaoJsonTLS
                );

                while (!paradaManual) {

                    Socket cliente = servidor.accept();
                    cliente.setSoTimeout(15000);
                    clientesAtivos.add(cliente);

                    try {
                        executor.execute(() -> atenderCliente(cliente));
                    } catch (RejectedExecutionException erro) {
                        clientesAtivos.remove(cliente);
                        fecharSocket(cliente);
                    }
                }
            }

        } catch (SocketException erro) {

            if (!paradaManual) {
                Util.registrarErroServidor(
                        "desktop",
                        "INTERNO/NÃO APLICÁVEL",
                        "NÃO APLICÁVEL",
                        "DesktopJsonRecepcaoThread.run",
                        erro
                );
            }

        } catch (GeneralSecurityException erro) {

            Util.registrarErroServidor(
                    "desktop",
                    "INTERNO/NÃO APLICÁVEL",
                    "NÃO APLICÁVEL",
                    "DesktopJsonRecepcaoThread.run - configuracao TLS",
                    erro
            );

            System.err.println(
                    "Não foi possível configurar o servidor TLS: "
                    + erro.getMessage()
            );

        } catch (IOException erro) {

            if (!paradaManual) {
                Util.registrarErroServidor(
                        "desktop",
                        "INTERNO/NÃO APLICÁVEL",
                        "NÃO APLICÁVEL",
                        "DesktopJsonRecepcaoThread.run",
                        erro
                );
            }

        } finally {

            receptor = null;
            fecharClientes();
            executor.shutdownNow();
            atendimentos = null;

            System.out.println("Servidor JSON TLS encerrado.");
        }
    }

    /**
     * Negocia TLS, recebe uma linha JSON, valida e processa a operacao.
     * A persistencia e realizada no MySQL antes de confirmar com ACK.
     */
    private void atenderCliente(Socket cliente) {

        String ip = cliente.getInetAddress().getHostAddress();

        try (Socket socket = cliente) {

            if (!(socket instanceof SSLSocket socketTLS)) {
                throw new IOException(
                        "Conexao recebida sem suporte TLS."
                );
            }

            socketTLS.startHandshake();

            try (
                    BufferedReader input = new BufferedReader(
                            new InputStreamReader(
                                    socketTLS.getInputStream(),
                                    StandardCharsets.UTF_8
                            )
                    );
                    BufferedWriter output = new BufferedWriter(
                            new OutputStreamWriter(
                                    socketTLS.getOutputStream(),
                                    StandardCharsets.UTF_8
                            )
                    )
            ) {

                String linhaJson = input.readLine();

                if (linhaJson == null || linhaJson.isBlank()) {
                    enviarResposta(
                            output,
                            criarErro(
                                    null,
                                    "JSON_VAZIO",
                                    "A mensagem JSON esta vazia."
                            )
                    );
                    return;
                }

                ChatMessage mensagem =
                        JsonProtocol.deserializeLine(linhaJson);

                // -----------------------------------------
                // CONSULTA DO HISTORICO NO MYSQL
                // -----------------------------------------
                if (mensagem.type() == ChatMessage.Type.HISTORY_REQUEST) {

                    if (mensagem.sender().isBlank()) {
                        enviarResposta(
                                output,
                                criarErro(
                                        mensagem.id(),
                                        "REMETENTE_INVALIDO",
                                        "O remetente deve ser informado."
                                )
                        );
                        return;
                    }

                    final String historico;
                    try {
                        historico = Util.obterHistoricoHtml();
                    } catch (IOException erroBanco) {
                        registrarErroBanco(
                                ip,
                                "DesktopJsonRecepcaoThread - consulta historico",
                                erroBanco
                        );
                        enviarResposta(
                                output,
                                criarErro(
                                        mensagem.id(),
                                        "ERRO_HISTORICO",
                                        "Nao foi possivel consultar o historico no banco de dados."
                                )
                        );
                        return;
                    }

                    ChatMessage respostaHistorico = new ChatMessage(
                            ChatMessage.CURRENT_VERSION,
                            null,
                            mensagem.id(),
                            ChatMessage.Type.HISTORY_RESPONSE,
                            "middleware",
                            mensagem.sender(),
                            historico,
                            "", "", "", "",
                            null,
                            Map.of(
                                    "status", "SUCCESS",
                                    "format", "text/html"
                            )
                    );

                    enviarResposta(output, respostaHistorico);

                    System.out.println(
                            "Historico JSON TLS enviado para "
                            + mensagem.sender() + " | IP: " + ip
                    );
                    return;
                }

                // -----------------------------------------
                // VALIDACAO DO ENVIO
                // -----------------------------------------
                if (mensagem.type() != ChatMessage.Type.SEND_MESSAGE) {
                    enviarResposta(
                            output,
                            criarErro(
                                    mensagem.id(),
                                    "OPERACAO_NAO_SUPORTADA",
                                    "Operacao nao suportada pelo middleware."
                            )
                    );
                    return;
                }

                if (mensagem.sender().isBlank()
                        || mensagem.sender().length() > 20) {
                    enviarResposta(
                            output,
                            criarErro(
                                    mensagem.id(),
                                    "REMETENTE_INVALIDO",
                                    "O remetente deve possuir entre 1 e 20 caracteres."
                            )
                    );
                    return;
                }

                if (mensagem.content().isBlank()
                        && mensagem.sticker().isBlank()) {
                    enviarResposta(
                            output,
                            criarErro(
                                    mensagem.id(),
                                    "CONTEUDO_VAZIO",
                                    "A mensagem nao pode estar vazia."
                            )
                    );
                    return;
                }

                String mensagemHtml =
                        RenderizadorMensagemHtml.renderizar(mensagem);

                // O registro estruturado e o HTML sao persistidos no MySQL.
                // Nenhum ACK e enviado se a gravacao falhar.
                try {
                    Util.registrarMensagem(mensagem, mensagemHtml);
                } catch (IOException erroBanco) {
                    registrarErroBanco(
                            ip,
                            "DesktopJsonRecepcaoThread - gravacao mensagem",
                            erroBanco
                    );
                    enviarResposta(
                            output,
                            criarErro(
                                    mensagem.id(),
                                    "ERRO_PERSISTENCIA",
                                    "Nao foi possivel registrar a mensagem no banco de dados."
                            )
                    );
                    return;
                }

                ChatMessage confirmacao = new ChatMessage(
                        ChatMessage.CURRENT_VERSION,
                        null,
                        mensagem.id(),
                        ChatMessage.Type.ACK,
                        "middleware",
                        mensagem.sender(),
                        "Mensagem recebida e registrada.",
                        "", "", "", "",
                        null,
                        Map.of("status", "SUCCESS")
                );

                enviarResposta(output, confirmacao);

                System.out.println(
                        "Mensagem JSON TLS persistida no MySQL: "
                        + mensagem.sender() + " | IP: " + ip
                );
            }

        } catch (Exception erro) {

            if (!paradaManual) {
                Util.registrarErroServidor(
                        "desktop",
                        "desktop",
                        ip,
                        "DesktopJsonRecepcaoThread.atenderCliente",
                        erro
                );
            }

        } finally {
            clientesAtivos.remove(cliente);
        }
    }

    private void registrarErroBanco(
            String ip,
            String origem,
            Exception erro) {

        Util.registrarErroServidor(
                "desktop",
                "MYSQL",
                ip,
                origem,
                erro
        );
    }

    private ChatMessage criarErro(
            String correlationId,
            String codigo,
            String descricao) {

        return new ChatMessage(
                ChatMessage.CURRENT_VERSION,
                null,
                correlationId,
                ChatMessage.Type.ERROR,
                "middleware",
                "",
                descricao,
                "", "", "", "",
                null,
                Map.of("code", codigo)
        );
    }

    private void enviarResposta(
            BufferedWriter output,
            ChatMessage resposta) throws IOException {

        output.write(JsonProtocol.serializeLine(resposta));
        output.flush();
    }

    public void fecharServidor() {

        paradaManual = true;

        try {
            if (receptor != null && !receptor.isClosed()) {
                receptor.close();
            }
        } catch (IOException erro) {
            System.err.println(
                    "Erro ao fechar servidor JSON TLS: " + erro.getMessage()
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
