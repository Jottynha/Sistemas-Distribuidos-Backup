package protocolo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessage(

        int protocolVersion,

        String id,

        String correlationId,

        Type type,

        String sender,

        String recipient,

        String content,

        String mode,

        String color,

        String avatar,

        String sticker,

        String sentAt,

        Map<String, Object> metadata

) {

    public static final int CURRENT_VERSION = 1;

    public ChatMessage {

        if (protocolVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Versao de protocolo invalida."
            );
        }

        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }

        type = Objects.requireNonNull(
                type,
                "O tipo da mensagem e obrigatorio."
        );

        sender = sender == null ? "" : sender.trim();

        recipient = recipient == null ? "" : recipient.trim();

        content = content == null ? "" : content;

        mode = mode == null ? "" : mode;

        color = color == null ? "" : color;

        avatar = avatar == null ? "" : avatar;

        sticker = sticker == null ? "" : sticker;

        sentAt = sentAt == null || sentAt.isBlank()
                ? Instant.now().toString()
                : sentAt;

        metadata = metadata == null
                ? Map.of()
                : Map.copyOf(metadata);
    }

    /**
     * Cria uma nova mensagem enviada por um cliente.
     */
    public static ChatMessage newMessage(
            String sender,
            String recipient,
            String content,
            String mode,
            String color,
            String avatar,
            String sticker
    ) {

        return new ChatMessage(

                CURRENT_VERSION,

                UUID.randomUUID().toString(),

                null,

                Type.SEND_MESSAGE,

                sender,

                recipient,

                content,

                mode,

                color,

                avatar,

                sticker,

                Instant.now().toString(),

                Map.of()
        );
    }

    /**
     * Operacoes aceitas pelo protocolo.
     */
    public enum Type {

        SEND_MESSAGE,

        MESSAGE,

        HISTORY_REQUEST,

        HISTORY_RESPONSE,

        ACK,

        ERROR,

        PING,

        PONG,

        ADVERTISEMENT_UPDATE
    }
}
