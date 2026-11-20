package pacote.replication;

import java.util.List;
import pacote.database.MensagemArmazenada;
import protocolo.ChatMessage;

/** Envelope interno entre os middlewares; nao altera o protocolo dos clientes. */
public record ReplicationEnvelope(
        int protocolVersion,
        String operation,
        String token,
        ChatMessage message,
        String renderedHtml,
        List<MensagemArmazenada> messages,
        Integer offset,
        Integer limit) {

    /** Mantem compatibilidade com as chamadas de replicacao individual. */
    public ReplicationEnvelope(
            int protocolVersion,
            String operation,
            String token,
            ChatMessage message,
            String renderedHtml) {
        this(protocolVersion, operation, token, message, renderedHtml, null, null, null);
    }

    public ReplicationEnvelope {
        if (messages != null) {
            messages = List.copyOf(messages);
        }
    }
}
