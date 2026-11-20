package pacote.replication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import protocolo.ChatMessage;
import protocolo.JsonProtocol;
import pacote.ConfiguracaoTLS;
import pacote.Util;

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
import java.security.MessageDigest;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Recebe replicacoes TLS do middleware principal e grava no banco reserva. */
public final class ReplicacaoRecepcaoThread implements Runnable {

    private static final int TAMANHO_MAXIMO_JSON = 1_048_576;
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String token;
    private final int porta;

    private volatile boolean paradaManual;
    private volatile SSLServerSocket receptor;
    private volatile ExecutorService atendimentos;

    private final Set<Socket> clientesAtivos = ConcurrentHashMap.newKeySet();

    public ReplicacaoRecepcaoThread() throws IOException {
        this.token = ReplicacaoConfig.tokenReceptor();
        this.porta = ReplicacaoConfig.portaReceptor();
    }

    /** Abre a porta sincronamente para que falhas de inicializacao sejam detectadas. */
    public synchronized void iniciar() throws IOException {
        if (receptor != null && !receptor.isClosed()) {
            return;
        }

        final SSLContext contexto;
        try {
            contexto = ConfiguracaoTLS.criarContextoServidor();
        } catch (GeneralSecurityException erro) {
            throw new IOException("Nao foi possivel carregar TLS para a replicacao.", erro);
        }

        SSLServerSocket novoServidor = null;
        ExecutorService novoExecutor = null;
        try {
            novoServidor = (SSLServerSocket) contexto.getServerSocketFactory().createServerSocket();
            novoServidor.setReuseAddress(true);
            novoServidor.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
            novoServidor.setNeedClientAuth(false);
            novoServidor.bind(new InetSocketAddress("0.0.0.0", porta), 50);

            novoExecutor = Executors.newVirtualThreadPerTaskExecutor();
            receptor = novoServidor;
            atendimentos = novoExecutor;
            paradaManual = false;

            Thread.ofVirtual()
                    .name("middleware-replicacao-accept")
                    .start(this);

            System.out.println("Receptor de replicacao TLS ativo na porta " + porta);
        } catch (IOException | RuntimeException erro) {
            if (novoExecutor != null) {
                novoExecutor.shutdownNow();
            }
            if (novoServidor != null) {
                try {
                    novoServidor.close();
                } catch (IOException fechamento) {
                    erro.addSuppressed(fechamento);
                }
            }
            receptor = null;
            atendimentos = null;
            throw new IOException("Nao foi possivel iniciar o receptor de replicacao na porta " + porta + ".", erro);
        }
    }

    @Override
    public void run() {
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
                        clientesAtivos.remove(cliente);
                        fecharSocket(cliente);
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
                registrarErro("ReplicacaoRecepcaoThread.run", erro);
            }
        } catch (IOException erro) {
            if (!paradaManual) {
                registrarErro("ReplicacaoRecepcaoThread.run", erro);
            }
        } finally {
            fecharClientes();
            System.out.println("Receptor de replicacao TLS encerrado.");
        }
    }

    private void atenderCliente(SSLSocket socket) {
        String ip = socket.getInetAddress().getHostAddress();
        try (socket) {
            socket.startHandshake();
            try (
                    BufferedReader input = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
                    );
                    BufferedWriter output = new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
                    )
            ) {
                String linha = input.readLine();
                if (linha == null || linha.isBlank()) {
                    enviarErro(output, null, "JSON_VAZIO", "A requisicao de replicacao esta vazia.");
                    return;
                }
                if (linha.length() > TAMANHO_MAXIMO_JSON) {
                    enviarErro(output, null, "JSON_MUITO_GRANDE", "A requisicao ultrapassa 1 MiB.");
                    return;
                }

                final ReplicationEnvelope envelope;
                try {
                    envelope = JSON.readValue(linha, ReplicationEnvelope.class);
                } catch (JsonProcessingException erro) {
                    enviarErro(output, null, "JSON_INVALIDO", "Nao foi possivel interpretar o envelope de replicacao.");
                    registrarErro("ReplicacaoRecepcaoThread - JSON invalido de " + ip, erro);
                    return;
                }

                ChatMessage resposta = processar(envelope, ip);
                output.write(JsonProtocol.serializeLine(resposta));
                output.flush();
            }
        } catch (Exception erro) {
            if (!paradaManual) {
                registrarErro("ReplicacaoRecepcaoThread.atenderCliente | IP: " + ip, erro);
            }
        } finally {
            clientesAtivos.remove(socket);
        }
    }

    private ChatMessage processar(ReplicationEnvelope envelope, String ip) {
        String correlationId = envelope == null || envelope.message() == null
                ? null
                : envelope.message().id();

        if (envelope == null
                || envelope.protocolVersion() != ChatMessage.CURRENT_VERSION
                || !"REPLICATE_MESSAGE".equals(envelope.operation())
                || envelope.message() == null) {
            return criarErro(correlationId, "ENVELOPE_INVALIDO", "Envelope de replicacao invalido.");
        }

        if (!tokenValido(envelope.token())) {
            return criarErro(correlationId, "REPLICACAO_NAO_AUTORIZADA", "Token de replicacao invalido.");
        }

        ChatMessage mensagem = envelope.message();
        if (mensagem.type() != ChatMessage.Type.SEND_MESSAGE) {
            return criarErro(correlationId, "TIPO_INVALIDO", "Somente mensagens SEND_MESSAGE podem ser replicadas.");
        }
        if (mensagem.sender().isBlank() || mensagem.sender().length() > 20) {
            return criarErro(correlationId, "REMETENTE_INVALIDO", "Remetente invalido na replicacao.");
        }
        if (mensagem.content().isBlank() && mensagem.sticker().isBlank()) {
            return criarErro(correlationId, "CONTEUDO_VAZIO", "A mensagem replicada nao possui conteudo.");
        }
        if (envelope.renderedHtml() == null || envelope.renderedHtml().isBlank()) {
            return criarErro(correlationId, "HTML_AUSENTE", "A representacao HTML da mensagem nao foi enviada.");
        }

        try {
            boolean inserida = Util.registrarMensagemReplicada(mensagem, envelope.renderedHtml());
            System.out.println(
                    (inserida ? "Mensagem replicada" : "Mensagem ja existente no Banco B")
                    + " | ID: " + mensagem.id()
                    + " | remetente: " + mensagem.sender()
                    + " | IP principal: " + ip
            );

            return new ChatMessage(
                    ChatMessage.CURRENT_VERSION,
                    null,
                    mensagem.id(),
                    ChatMessage.Type.ACK,
                    "middleware-reserva",
                    mensagem.sender(),
                    inserida ? "Mensagem persistida no Banco B." : "Mensagem ja estava persistida no Banco B.",
                    "", "", "", "",
                    Instant.now().toString(),
                    Map.of("status", "SUCCESS", "duplicate", !inserida)
            );
        } catch (IOException erro) {
            registrarErro("ReplicacaoRecepcaoThread - persistencia Banco B", erro);
            return criarErro(correlationId, "ERRO_PERSISTENCIA", "Nao foi possivel persistir a mensagem no Banco B.");
        }
    }

    private boolean tokenValido(String recebido) {
        if (recebido == null) {
            return false;
        }
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                recebido.getBytes(StandardCharsets.UTF_8)
        );
    }

    private ChatMessage criarErro(String correlationId, String codigo, String descricao) {
        return new ChatMessage(
                ChatMessage.CURRENT_VERSION,
                null,
                correlationId,
                ChatMessage.Type.ERROR,
                "middleware-reserva",
                "",
                descricao,
                "", "", "", "",
                Instant.now().toString(),
                Map.of("code", codigo)
        );
    }

    private void enviarErro(BufferedWriter output, String correlationId, String codigo, String descricao)
            throws IOException {
        output.write(JsonProtocol.serializeLine(criarErro(correlationId, codigo, descricao)));
        output.flush();
    }

    private void registrarErro(String origem, Throwable erro) {
        Util.registrarErroServidor(
                "internal",
                "REPLICACAO",
                "N/A",
                origem,
                erro
        );
    }

    public synchronized void fecharServidor() {
        paradaManual = true;
        SSLServerSocket servidor = receptor;
        receptor = null;
        if (servidor != null && !servidor.isClosed()) {
            try {
                servidor.close();
            } catch (IOException erro) {
                registrarErro("ReplicacaoRecepcaoThread.fecharServidor", erro);
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
