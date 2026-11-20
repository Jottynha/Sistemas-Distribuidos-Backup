package pacote.replication;

import protocolo.ChatMessage;

/** Envelope interno entre os middlewares; nao altera o protocolo dos clientes. */
public record ReplicationEnvelope(
        int protocolVersion,
        String operation,
        String token,
        ChatMessage message,
        String renderedHtml) {
}
