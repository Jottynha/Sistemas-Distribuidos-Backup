package pacote.replication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import protocolo.ChatMessage;
import protocolo.JsonProtocol;
import pacote.ConfiguracaoTLS;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

/** Envia uma mensagem persistida no Banco A ao middleware reserva por TLS. */
public final class ReplicacaoClient {

    private static final int TAMANHO_MAXIMO_JSON = 1_048_576;
    private static final ObjectMapper JSON = new ObjectMapper();

    private ReplicacaoClient() {
    }

    public static void replicar(
            ChatMessage mensagem,
            String renderedHtml,
            ReplicacaoConfig config) throws IOException {

        if (mensagem == null || renderedHtml == null || renderedHtml.isBlank()) {
            throw new IllegalArgumentException("Mensagem e HTML sao obrigatorios para a replicacao.");
        }

        ReplicationEnvelope envelope = new ReplicationEnvelope(
                ChatMessage.CURRENT_VERSION,
                "REPLICATE_MESSAGE",
                config.token(),
                mensagem,
                renderedHtml
        );

        final String linha;
        try {
            linha = JSON.writeValueAsString(envelope) + "\n";
        } catch (JsonProcessingException erro) {
            throw new IOException("Nao foi possivel serializar a mensagem para replicacao.", erro);
        }

        if (linha.getBytes(StandardCharsets.UTF_8).length > TAMANHO_MAXIMO_JSON) {
            throw new IOException("A mensagem e seu HTML ultrapassam o limite de 1 MiB do protocolo de replicacao.");
        }

        final SSLContext contexto;
        try {
            contexto = ConfiguracaoTLS.criarContextoCliente(config.caFile());
        } catch (GeneralSecurityException erro) {
            throw new IOException("Nao foi possivel preparar a validacao TLS do middleware reserva.", erro);
        }

        try (SSLSocket socket = (SSLSocket) contexto.getSocketFactory().createSocket()) {
            socket.connect(new InetSocketAddress(config.host(), config.port()), config.connectTimeoutMs());
            socket.setSoTimeout(config.readTimeoutMs());
            socket.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});

            SSLParameters parametros = socket.getSSLParameters();
            parametros.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parametros);
            socket.startHandshake();

            try (
                    BufferedWriter output = new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
                    );
                    BufferedReader input = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
                    )
            ) {
                output.write(linha);
                output.flush();

                String respostaJson = input.readLine();
                if (respostaJson == null || respostaJson.isBlank()) {
                    throw new IOException("O middleware reserva encerrou a conexao sem confirmar a replicacao.");
                }
                if (respostaJson.length() > TAMANHO_MAXIMO_JSON) {
                    throw new IOException("A resposta do middleware reserva ultrapassa 1 MiB.");
                }

                ChatMessage resposta = JsonProtocol.deserializeLine(respostaJson);
                if (resposta.correlationId() == null || !resposta.correlationId().equals(mensagem.id())) {
                    throw new IOException("A resposta do middleware reserva nao corresponde a mensagem enviada.");
                }
                if (resposta.type() == ChatMessage.Type.ERROR) {
                    Object codigo = resposta.metadata().getOrDefault("code", "ERRO_REPLICACAO");
                    throw new IOException("Middleware reserva recusou a replicacao (" + codigo + "): " + resposta.content());
                }
                if (resposta.type() != ChatMessage.Type.ACK
                        || !"SUCCESS".equals(resposta.metadata().get("status"))) {
                    throw new IOException("O middleware reserva retornou uma confirmacao invalida.");
                }
            }
        }
    }
}
