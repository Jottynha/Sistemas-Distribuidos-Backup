package pacote.replication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import pacote.ConfiguracaoTLS;
import pacote.Util;
import pacote.database.MensagemArmazenada;
import pacote.database.MensagemDAO;
import protocolo.ChatMessage;
import protocolo.JsonProtocol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

/** Cliente TLS usado para replicacao, sincronizacao e transferencia de lideranca. */
public final class ReplicacaoClient {

    private static final int TAMANHO_MAXIMO_JSON = 1_048_576;
    private static final int LOTE_INICIAL = 25;
    private static final ObjectMapper JSON = new ObjectMapper();

    private ReplicacaoClient() { }

    /** Replicacao sincrona de uma nova mensagem do principal para a reserva. */
    public static void replicar(
            ChatMessage mensagem,
            String renderedHtml,
            ReplicacaoConfig config) throws IOException {

        if (mensagem == null || renderedHtml == null || renderedHtml.isBlank()) {
            throw new IllegalArgumentException("Mensagem e HTML sao obrigatorios para a replicacao.");
        }
        ReplicationEnvelope envelope = new ReplicationEnvelope(
                ChatMessage.CURRENT_VERSION, "REPLICATE_MESSAGE", config.token(), mensagem, renderedHtml);
        ChatMessage resposta = trocar(envelope, config);
        validarResposta(resposta, mensagem.id());
        Object role = resposta.metadata().get("role");
        if ("ACTIVE".equals(role)) {
            throw new IOException("O middleware reserva ja foi promovido e nao aceita novas replicacoes do antigo principal.");
        }
    }

    /** Retorna STANDBY ou ACTIVE apos renovar o heartbeat TLS do principal. */
    public static String consultarEstadoReserva(ReplicacaoConfig config) throws IOException {
        ChatMessage ping = novaSolicitacao("HEARTBEAT", Map.of("purpose", "failover-heartbeat"));
        ReplicationEnvelope envelope = new ReplicationEnvelope(
                ChatMessage.CURRENT_VERSION, "HEARTBEAT", config.token(), ping, "");
        ChatMessage resposta = trocar(envelope, config);
        validarResposta(resposta, ping.id());
        Object role = resposta.metadata().get("role");
        if (!(role instanceof String valor) || !(valor.equals("ACTIVE") || valor.equals("STANDBY"))) {
            throw new IOException("O reserva retornou um estado de failover invalido.");
        }
        return valor;
    }

    /**
     * Sincroniza os dois bancos quando a reserva esta em STANDBY.
     * Primeiro importa B -> A, depois envia A -> B e por fim compara os resumos.
     */
    public static void sincronizarReserva(ReplicacaoConfig config) throws IOException {
        sincronizarBilateral(config);
    }

    /**
     * Reintegra A quando B esta ACTIVE. B suspende temporariamente os listeners,
     * os bancos sao reconciliados e B libera formalmente a lideranca.
     */
    public static void reintegrarPrincipal(ReplicacaoConfig config) throws IOException {
        ChatMessage solicitacao = novaSolicitacao("PREPARE_FAILBACK", Map.of("purpose", "controlled-failback"));
        ReplicationEnvelope envelope = new ReplicationEnvelope(
                ChatMessage.CURRENT_VERSION, "PREPARE_FAILBACK", config.token(), solicitacao, "");
        ChatMessage resposta = trocar(envelope, config);
        validarResposta(resposta, solicitacao.id());

        String state = textoMetadata(resposta, "state");
        boolean reservaQuiescida = "QUIESCED".equals(state);
        if (!reservaQuiescida && !"STANDBY".equals(state)) {
            throw new IOException("A reserva nao confirmou a suspensao segura para failback.");
        }

        try {
            sincronizarBilateral(config);

            if (reservaQuiescida) {
                MensagemDAO.ResumoMensagens resumo = Util.obterResumoMensagens();
                ChatMessage concluir = novaSolicitacao("COMPLETE_FAILBACK", Map.of(
                        "count", resumo.quantidade(),
                        "digest", resumo.sha256()));
                ReplicationEnvelope conclusao = new ReplicationEnvelope(
                        ChatMessage.CURRENT_VERSION, "COMPLETE_FAILBACK", config.token(), concluir, "");
                ChatMessage confirmacao = trocar(conclusao, config);
                validarResposta(confirmacao, concluir.id());
                if (!"STANDBY".equals(confirmacao.metadata().get("role"))) {
                    throw new IOException("B nao confirmou o retorno ao estado STANDBY.");
                }
            }
        } catch (IOException erro) {
            if (reservaQuiescida) {
                try {
                    ChatMessage cancelar = novaSolicitacao("CANCEL_FAILBACK", Map.of("purpose", "abort-failback"));
                    ReplicationEnvelope cancelamento = new ReplicationEnvelope(
                            ChatMessage.CURRENT_VERSION, "CANCEL_FAILBACK", config.token(), cancelar, "");
                    ChatMessage confirmacaoCancelamento = trocar(cancelamento, config);
                    validarResposta(confirmacaoCancelamento, cancelar.id());
                } catch (Exception erroCancelamento) {
                    erro.addSuppressed(erroCancelamento);
                }
            }
            throw erro;
        }
    }

    private static void sincronizarBilateral(ReplicacaoConfig config) throws IOException {
        System.out.println("[SINCRONIZACAO] Importando registros da reserva para o banco local...");
        copiarReservaParaLocal(config);

        System.out.println("[SINCRONIZACAO] Enviando registros locais para o banco da reserva...");
        copiarLocalParaReserva(config);

        MensagemDAO.ResumoMensagens local = Util.obterResumoMensagens();
        MensagemDAO.ResumoMensagens remoto = consultarResumoReserva(config);
        if (local.quantidade() != remoto.quantidade() || !local.sha256().equals(remoto.sha256())) {
            throw new IOException(
                    "Os bancos ainda divergem apos a sincronizacao. Local="
                    + local.quantidade() + "/" + local.sha256()
                    + ", reserva=" + remoto.quantidade() + "/" + remoto.sha256()
            );
        }

        System.out.println("[SINCRONIZACAO] Bancos consistentes: " + local.quantidade() + " mensagens.");
    }

    private static void copiarReservaParaLocal(ReplicacaoConfig config) throws IOException {
        int offset = 0;
        int limite = LOTE_INICIAL;
        long total = -1;

        while (total < 0 || offset < total) {
            ChatMessage pedido = novaSolicitacao("SYNC_EXPORT", Map.of("purpose", "database-reconciliation"));
            ReplicationEnvelope envelope = new ReplicationEnvelope(
                    ChatMessage.CURRENT_VERSION, "SYNC_EXPORT", config.token(), pedido, "",
                    null, offset, limite);

            final ChatMessage resposta;
            try {
                resposta = trocar(envelope, config);
                validarResposta(resposta, pedido.id());
            } catch (IOException erro) {
                if (limite > 1 && erroDeLoteGrande(erro)) {
                    limite = Math.max(1, limite / 2);
                    continue;
                }
                throw erro;
            }

            if (total < 0) {
                total = numeroMetadata(resposta, "total");
            }

            final List<MensagemArmazenada> lote;
            try {
                lote = JSON.readValue(
                        resposta.content(),
                        new TypeReference<List<MensagemArmazenada>>() { });
            } catch (JsonProcessingException | IllegalArgumentException erro) {
                throw new IOException("A reserva enviou um lote de mensagens invalido.", erro);
            }

            if (lote.isEmpty() && offset < total) {
                throw new IOException("A reserva encerrou a exportacao antes de transferir todos os registros.");
            }

            for (MensagemArmazenada registro : lote) {
                Util.registrarMensagemReplicada(registro.mensagem(), registro.renderedHtml());
            }
            offset += lote.size();
            if (lote.size() < limite && offset < total) {
                throw new IOException("A pagina de exportacao da reserva veio incompleta.");
            }
        }
    }

    private static void copiarLocalParaReserva(ReplicacaoConfig config) throws IOException {
        int offset = 0;
        int limite = LOTE_INICIAL;

        while (true) {
            List<MensagemArmazenada> lote = Util.listarMensagensParaReplicacao(offset, limite);
            if (lote.isEmpty()) {
                break;
            }

            ChatMessage pedido = novaSolicitacao("SYNC_IMPORT_BATCH", Map.of("purpose", "database-reconciliation"));
            ReplicationEnvelope envelope = new ReplicationEnvelope(
                    ChatMessage.CURRENT_VERSION, "SYNC_IMPORT_BATCH", config.token(), pedido, "",
                    lote, null, null);

            try {
                ChatMessage resposta = trocar(envelope, config);
                validarResposta(resposta, pedido.id());
                offset += lote.size();
            } catch (IOException erro) {
                if (limite > 1 && erroDeLoteGrande(erro)) {
                    limite = Math.max(1, limite / 2);
                    continue;
                }
                throw erro;
            }
        }
    }

    private static MensagemDAO.ResumoMensagens consultarResumoReserva(ReplicacaoConfig config)
            throws IOException {
        ChatMessage pedido = novaSolicitacao("SYNC_STATUS", Map.of("purpose", "database-verification"));
        ReplicationEnvelope envelope = new ReplicationEnvelope(
                ChatMessage.CURRENT_VERSION, "SYNC_STATUS", config.token(), pedido, "");
        ChatMessage resposta = trocar(envelope, config);
        validarResposta(resposta, pedido.id());
        return new MensagemDAO.ResumoMensagens(
                numeroMetadata(resposta, "count"),
                textoMetadata(resposta, "digest")
        );
    }

    private static ChatMessage novaSolicitacao(String content, Map<String, Object> metadata) {
        return new ChatMessage(
                ChatMessage.CURRENT_VERSION,
                UUID.randomUUID().toString(),
                null,
                ChatMessage.Type.PING,
                "middleware-principal",
                "middleware-reserva",
                content,
                "", "", "", "",
                Instant.now().toString(),
                metadata
        );
    }

    private static ChatMessage trocar(ReplicationEnvelope envelope, ReplicacaoConfig config)
            throws IOException {
        final String linha;
        try {
            linha = JSON.writeValueAsString(envelope) + "\n";
        } catch (JsonProcessingException erro) {
            throw new IOException("Nao foi possivel serializar o envelope de replicacao.", erro);
        }
        if (linha.getBytes(StandardCharsets.UTF_8).length > TAMANHO_MAXIMO_JSON) {
            throw new IOException("O envelope de replicacao ultrapassa 1 MiB; reduza o tamanho do lote.");
        }

        final SSLContext contexto;
        try {
            contexto = ConfiguracaoTLS.criarContextoCliente(config.caFile());
        } catch (GeneralSecurityException erro) {
            throw new IOException("Nao foi possivel preparar a validacao TLS do reserva.", erro);
        }

        try (SSLSocket socket = (SSLSocket) contexto.getSocketFactory().createSocket()) {
            socket.connect(new InetSocketAddress(config.host(), config.port()), config.connectTimeoutMs());
            socket.setSoTimeout(config.readTimeoutMs());
            socket.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
            SSLParameters parametros = socket.getSSLParameters();
            parametros.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parametros);
            socket.startHandshake();

            try (BufferedWriter output = new BufferedWriter(
                         new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                 BufferedReader input = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                output.write(linha);
                output.flush();
                String respostaJson = input.readLine();
                if (respostaJson == null || respostaJson.isBlank()) {
                    throw new IOException("O reserva encerrou a conexao sem responder.");
                }
                if (respostaJson.getBytes(StandardCharsets.UTF_8).length > TAMANHO_MAXIMO_JSON) {
                    throw new IOException("Resposta do reserva ultrapassa 1 MiB.");
                }
                return JsonProtocol.deserializeLine(respostaJson);
            }
        }
    }

    private static boolean erroDeLoteGrande(IOException erro) {
        String mensagem = erro.getMessage();
        return mensagem != null && (
                mensagem.contains("LOTE_MUITO_GRANDE")
                || mensagem.contains("ultrapassa 1 MiB")
                || mensagem.contains("ultrapassa 1 MiB".toLowerCase()));
    }

    private static long numeroMetadata(ChatMessage resposta, String campo) throws IOException {
        Object valor = resposta.metadata().get(campo);
        if (valor instanceof Number numero) return numero.longValue();
        if (valor instanceof String texto) {
            try {
                return Long.parseLong(texto);
            } catch (NumberFormatException erro) {
                throw new IOException("A resposta do reserva possui o campo numerico invalido: " + campo, erro);
            }
        }
        throw new IOException("A resposta do reserva nao informou o campo numerico: " + campo);
    }

    private static String textoMetadata(ChatMessage resposta, String campo) throws IOException {
        Object valor = resposta.metadata().get(campo);
        if (valor instanceof String texto && !texto.isBlank()) return texto;
        throw new IOException("A resposta do reserva nao informou o campo: " + campo);
    }

    private static void validarResposta(ChatMessage resposta, String id) throws IOException {
        if (resposta.correlationId() == null || !resposta.correlationId().equals(id)) {
            throw new IOException("A resposta do reserva nao corresponde a requisicao enviada.");
        }
        if (resposta.type() == ChatMessage.Type.ERROR) {
            Object codigo = resposta.metadata().getOrDefault("code", "ERRO_REPLICACAO");
            throw new IOException("Middleware reserva recusou a operacao (" + codigo + "): " + resposta.content());
        }
        if (resposta.type() != ChatMessage.Type.ACK || !"SUCCESS".equals(resposta.metadata().get("status"))) {
            throw new IOException("O middleware reserva retornou uma confirmacao invalida.");
        }
    }
}
