
package pacote;

import protocolo.ChatMessage;
import protocolo.JsonProtocol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import java.nio.charset.StandardCharsets;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class DesktopJsonRecepcaoThread implements Runnable {

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
                            Util.PortaRecepcaoJson
                    )
            );

            System.out.println(
                    "Servidor JSON ativo na porta "
                    + Util.PortaRecepcaoJson
            );

            while (!paradaManual) {

                Socket cliente = servidor.accept();

                cliente.setSoTimeout(15000);

                clientesAtivos.add(cliente);

                try {

                    executor.execute(
                            () -> atenderCliente(cliente)
                    );

                } catch (RejectedExecutionException erro) {

                    clientesAtivos.remove(cliente);

                    fecharSocket(cliente);
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

            System.out.println(
                    "Servidor JSON encerrado."
            );
        }
    }

    /**
     * Recebe uma linha JSON, valida e processa a mensagem.
     */
    private void atenderCliente(Socket cliente) {

        String ip = cliente.getInetAddress()
                .getHostAddress();

        try (Socket socket = cliente;

             BufferedReader input =
                     new BufferedReader(
                             new InputStreamReader(
                                     socket.getInputStream(),
                                     StandardCharsets.UTF_8
                             )
                     );

             BufferedWriter output =
                     new BufferedWriter(
                             new OutputStreamWriter(
                                     socket.getOutputStream(),
                                     StandardCharsets.UTF_8
                             )
                     )) {

            String linhaJson = input.readLine();

            if (linhaJson == null || linhaJson.isBlank()) {
                throw new IllegalArgumentException(
                        "Mensagem JSON vazia."
                );
            }

            ChatMessage mensagem =
                    JsonProtocol.deserializeLine(linhaJson);

            // Operacao 1: consulta do historico.
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

                try {

                    String historico = Util.obterHistoricoHtml();

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

                            java.util.Map.of(
                                    "status", "SUCCESS"
                            )
                    );

                    enviarResposta(output, respostaHistorico);

                    System.out.println(
                            "Historico JSON enviado para "
                            + mensagem.sender()
                            + " | IP: " + ip
                    );

                } catch (IOException erro) {

                    enviarResposta(
                            output,
                            criarErro(
                                    mensagem.id(),
                                    "ERRO_HISTORICO",
                                    "Nao foi possivel consultar o historico."
                            )
                    );

                    throw erro;
                }

                return;
            }

            // Operacao 2: recebimento de mensagem.
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

            if (mensagem.content().isBlank()) {

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

            // Converte a mensagem estruturada para o formato
            // HTML temporariamente utilizado pelo sistema antigo.
            String mensagemHtml = RenderizadorMensagemHtml.renderizar(mensagem);

            // Persistencia temporaria no repositorio TXT.
            Util.adicionarMensagem(mensagemHtml);

            // Confirma somente depois da persistencia.
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

                    java.util.Map.of(
                            "status", "SUCCESS"
                    )
            );

            enviarResposta(output, confirmacao);

            System.out.println(
                    "Mensagem JSON recebida de "
                    + mensagem.sender()
                    + " | IP: " + ip
            );

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

    /**
     * Converte os dados JSON em HTML compativel com o historico antigo.
     */
    private String converterParaHtml(ChatMessage mensagem) {

        String remetente = escaparHtml(mensagem.sender());

        String conteudo = escaparHtml(mensagem.content());

        String cor = mensagem.color();

        // Aceita apenas cores hexadecimais.
        if (cor == null || !cor.matches("^#[0-9a-fA-F]{6}$")) {
            cor = "#333333";
        }

        String modo = mensagem.mode() == null
                ? ""
                : mensagem.mode().toUpperCase();

        if ("GRITA".equals(modo)) {
            conteudo = conteudo.toUpperCase();
        }

        return "<div class=\"mensagem\">"
                + "<strong style=\"color:" + cor + "\">"
                + remetente
                + "</strong>: "
                + conteudo
                + "</div>";
    }

    /**
     * Impede a insercao de HTML arbitrario no historico.
     */
    private String escaparHtml(String texto) {

        if (texto == null) {
            return "";
        }

        return texto
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("\r\n", "<br>")
                .replace("\n", "<br>")
                .replace("\r", "<br>");
    }

    /**
     * Cria uma resposta de erro estruturada.
     */
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

                java.util.Map.of(
                        "code", codigo
                )
        );
    }

    /**
     * Envia uma resposta JSON terminada em quebra de linha.
     */
    private void enviarResposta(
            BufferedWriter output,
            ChatMessage resposta) throws IOException {

        output.write(JsonProtocol.serializeLine(resposta));

        output.flush();
    }

    /**
     * Encerra o servidor.
     */
    public void fecharServidor() {

        paradaManual = true;

        try {

            if (receptor != null && !receptor.isClosed()) {
                receptor.close();
            }

        } catch (IOException erro) {

            System.err.println(
                    "Erro ao fechar servidor JSON: "
                    + erro.getMessage()
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
