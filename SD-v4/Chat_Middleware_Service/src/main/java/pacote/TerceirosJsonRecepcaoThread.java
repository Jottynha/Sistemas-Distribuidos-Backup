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

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Servidor JSON/TLS destinado a clientes desenvolvidos em outras linguagens.
 * Utiliza o mesmo protocolo e a mesma persistencia MySQL dos clientes Java.
 */
public class TerceirosJsonRecepcaoThread {

    public static final int PORTA = 6665;
    private static final int TAMANHO_MAXIMO_JSON = 1_048_576;

    private volatile boolean paradaManual = false;
    private volatile SSLServerSocket receptor;
    private volatile ExecutorService atendimentos;

    private final Set<Socket> clientesAtivos =
            ConcurrentHashMap.newKeySet();

    public synchronized void iniciar() throws IOException {

        if (isRunning()) {
            return;
        }

        // Impede iniciar o listener quando o MySQL esta indisponivel.
        Util.inicializarRepositorio();

        SSLContext contexto;
        try {
            contexto = ConfiguracaoTLS.criarContextoServidor();
        } catch (GeneralSecurityException erro) {
            throw new IOException(
                    "Nao foi possivel carregar a configuracao TLS do servico de terceiros.",
                    erro
            );
        }

        SSLServerSocket novoServidor = null;
        ExecutorService novoExecutor = null;

        try {
            novoServidor = (SSLServerSocket) contexto
                    .getServerSocketFactory()
                    .createServerSocket();

            novoServidor.setReuseAddress(true);
            novoServidor.setEnabledProtocols(
                    new String[]{"TLSv1.3", "TLSv1.2"}
            );
            novoServidor.setNeedClientAuth(false);
            novoServidor.bind(
                    new InetSocketAddress("0.0.0.0", PORTA),
                    50
            );

            novoExecutor = Executors.newVirtualThreadPerTaskExecutor();

            receptor = novoServidor;
            atendimentos = novoExecutor;
            paradaManual = false;

            Thread.ofVirtual()
                    .name("terceiros-json-tls-accept")
                    .start(this::aceitarConexoes);

            System.out.println(
                    "Servico Terceiros JSON TLS ativo na porta " + PORTA
            );

        } catch (IOException | RuntimeException erro) {
            if (novoExecutor != null) {
                novoExecutor.shutdownNow();
            }
            if (novoServidor != null) {
                try {
                    novoServidor.close();
                } catch (IOException erroFechamento) {
                    erro.addSuppressed(erroFechamento);
                }
            }
            receptor = null;
            atendimentos = null;
            throw new IOException(
                    "Nao foi possivel iniciar o servico TLS para terceiros na porta "
                    + PORTA + ".",
                    erro
            );
        }
    }

    private void aceitarConexoes() {

        try {
            while (!paradaManual) {
                SSLServerSocket servidor = receptor;
                if (servidor == null || servidor.isClosed()) {
                    break;
                }

                SSLSocket cliente = (SSLSocket) servidor.accept();
                cliente.setSoTimeout(15000);
                clientesAtivos.add(cliente);

                ExecutorService executor = atendimentos;
                try {
                    if (executor == null || executor.isShutdown()) {
                        fecharSocket(cliente);
                        clientesAtivos.remove(cliente);
                        continue;
                    }
                    executor.execute(() -> atenderCliente(cliente));
                } catch (RejectedExecutionException erro) {
                    clientesAtivos.remove(cliente);
                    fecharSocket(cliente);
                }
            }

        } catch (SocketException erro) {
            if (!paradaManual) {
                registrarErro(
                        "TerceirosJsonRecepcaoThread.aceitarConexoes",
                        erro
                );
            }

        } catch (IOException erro) {
            if (!paradaManual) {
                registrarErro(
                        "TerceirosJsonRecepcaoThread.aceitarConexoes",
                        erro
                );
            }

        } finally {
            fecharClientes();
            System.out.println("Servico Terceiros JSON TLS encerrado.");
        }
    }

    private void atenderCliente(Socket cliente) {

        String ip = cliente.getInetAddress().getHostAddress();

        try (SSLSocket socket = (SSLSocket) cliente) {

            socket.startHandshake();

            try (
                    BufferedReader input = new BufferedReader(
                            new InputStreamReader(
                                    socket.getInputStream(),
                                    StandardCharsets.UTF_8
                            )
                    );
                    BufferedWriter output = new BufferedWriter(
                            new OutputStreamWriter(
                                    socket.getOutputStream(),
                                    StandardCharsets.UTF_8
                            )
                    )
            ) {
                String linhaJson = input.readLine();

                if (linhaJson == null || linhaJson.isBlank()) {
                    enviarResposta(
                            output,
                            criarErro(null, "JSON_VAZIO", "A requisicao JSON esta vazia.")
                    );
                    return;
                }

                if (linhaJson.length() > TAMANHO_MAXIMO_JSON) {
                    enviarResposta(
                            output,
                            criarErro(
                                    null,
                                    "JSON_MUITO_GRANDE",
                                    "A requisicao ultrapassa o limite permitido."
                            )
                    );
                    return;
                }

                ChatMessage requisicao;
                try {
                    requisicao = JsonProtocol.deserializeLine(linhaJson);
                } catch (Exception erro) {
                    Util.registrarErroServidor(
                            "desktop",
                            "TERCEIROS",
                            ip,
                            "TerceirosJsonRecepcaoThread.deserializeLine",
                            erro
                    );
                    enviarResposta(
                            output,
                            criarErro(
                                    null,
                                    "JSON_INVALIDO",
                                    "Nao foi possivel interpretar o JSON recebido."
                            )
                    );
                    return;
                }

                ChatMessage resposta = processarRequisicao(requisicao, ip);
                enviarResposta(output, resposta);
            }

        } catch (Exception erro) {
            if (!paradaManual) {
                Util.registrarErroServidor(
                        "desktop",
                        "TERCEIROS",
                        ip,
                        "TerceirosJsonRecepcaoThread.atenderCliente",
                        erro
                );
            }

        } finally {
            clientesAtivos.remove(cliente);
        }
    }

    private ChatMessage processarRequisicao(
            ChatMessage requisicao,
            String ip) throws IOException {

        if (requisicao.type() == ChatMessage.Type.HISTORY_REQUEST) {

            if (requisicao.sender().isBlank()) {
                return criarErro(
                        requisicao.id(),
                        "REMETENTE_INVALIDO",
                        "O remetente deve ser informado."
                );
            }

            final String historico;
            try {
                historico = Util.obterHistoricoHtml();
            } catch (IOException erroBanco) {
                registrarErro(
                        "TerceirosJsonRecepcaoThread - consulta historico MySQL",
                        erroBanco
                );
                return criarErro(
                        requisicao.id(),
                        "ERRO_HISTORICO",
                        "Nao foi possivel consultar o historico no banco de dados."
                );
            }

            System.out.println(
                    "Historico JSON TLS consultado no MySQL por: "
                    + requisicao.sender() + " | IP: " + ip
            );

            return new ChatMessage(
                    ChatMessage.CURRENT_VERSION,
                    null,
                    requisicao.id(),
                    ChatMessage.Type.HISTORY_RESPONSE,
                    "middleware",
                    requisicao.sender(),
                    historico,
                    "", "", "", "",
                    Instant.now().toString(),
                    Map.of(
                            "status", "SUCCESS",
                            "format", "text/html"
                    )
            );
        }

        if (requisicao.type() == ChatMessage.Type.SEND_MESSAGE) {

            if (requisicao.sender().isBlank()
                    || requisicao.sender().length() > 20) {
                return criarErro(
                        requisicao.id(),
                        "REMETENTE_INVALIDO",
                        "O remetente deve possuir entre 1 e 20 caracteres."
                );
            }

            if (requisicao.content().isBlank()
                    && requisicao.sticker().isBlank()) {
                return criarErro(
                        requisicao.id(),
                        "CONTEUDO_VAZIO",
                        "A mensagem nao pode estar vazia."
                );
            }

            String mensagemHtml =
                    RenderizadorMensagemHtml.renderizar(requisicao);

            // O ACK so e produzido depois da confirmacao do INSERT no MySQL.
            try {
                Util.registrarMensagem(requisicao, mensagemHtml);
            } catch (IOException erroBanco) {
                registrarErro(
                        "TerceirosJsonRecepcaoThread - gravacao MySQL",
                        erroBanco
                );
                return criarErro(
                        requisicao.id(),
                        "ERRO_PERSISTENCIA",
                        "Nao foi possivel registrar a mensagem no banco de dados."
                );
            }

            System.out.println(
                    "Mensagem JSON TLS persistida no MySQL: "
                    + requisicao.sender() + " | IP: " + ip
            );

            return new ChatMessage(
                    ChatMessage.CURRENT_VERSION,
                    null,
                    requisicao.id(),
                    ChatMessage.Type.ACK,
                    "middleware",
                    requisicao.sender(),
                    "Mensagem recebida e registrada.",
                    "", "", "", "",
                    Instant.now().toString(),
                    Map.of("status", "SUCCESS")
            );
        }

        return criarErro(
                requisicao.id(),
                "OPERACAO_NAO_SUPORTADA",
                "Operacao nao suportada pelo servico de terceiros."
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
                Instant.now().toString(),
                Map.of("code", codigo)
        );
    }

    private void enviarResposta(
            BufferedWriter output,
            ChatMessage resposta) throws IOException {

        output.write(JsonProtocol.serializeLine(resposta));
        output.flush();
    }

    private void registrarErro(String origem, Throwable erro) {
        Util.registrarErroServidor(
                "desktop",
                "TERCEIROS",
                "NÃO APLICÁVEL",
                origem,
                erro
        );
    }

    public synchronized boolean isRunning() {
        SSLServerSocket servidor = receptor;
        return !paradaManual
                && servidor != null
                && servidor.isBound()
                && !servidor.isClosed();
    }

    public synchronized void fecharServidor() {

        paradaManual = true;

        SSLServerSocket servidor = receptor;
        receptor = null;

        if (servidor != null && !servidor.isClosed()) {
            try {
                servidor.close();
            } catch (IOException erro) {
                registrarErro(
                        "TerceirosJsonRecepcaoThread.fecharServidor",
                        erro
                );
            }
        }

        fecharClientes();

        ExecutorService executor = atendimentos;
        atendimentos = null;

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
