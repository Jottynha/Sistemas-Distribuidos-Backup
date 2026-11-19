
package protocolo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

public final class JsonProtocol {

    private static final int MAX_JSON_LENGTH = 1_048_576;

    private static final ObjectMapper MAPPER =
            JsonMapper.builder()
                    .configure(
                            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                            false
                    )
                    .build();

    private JsonProtocol() {
    }

    /**
     * Converte uma mensagem Java para JSON.
     */
    public static String serialize(ChatMessage message)
            throws JsonProcessingException {

        if (message == null) {
            throw new IllegalArgumentException(
                    "A mensagem nao pode ser nula."
            );
        }

        return MAPPER.writeValueAsString(message);
    }

    /**
     * Converte uma mensagem Java para JSON,
     * incluindo quebra de linha para transmissao TCP.
     */
    public static String serializeLine(ChatMessage message)
            throws JsonProcessingException {

        return serialize(message) + "\n";
    }

    /**
     * Converte um JSON recebido em objeto Java.
     */
    public static ChatMessage deserialize(String json)
            throws JsonProcessingException {

        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException(
                    "O JSON recebido esta vazio."
            );
        }

        if (json.length() > MAX_JSON_LENGTH) {
            throw new IllegalArgumentException(
                    "O JSON recebido ultrapassa o limite permitido."
            );
        }

        ChatMessage message = MAPPER.readValue(
                json,
                ChatMessage.class
        );

        if (message == null) {
            throw new IllegalArgumentException(
                    "Nao foi possivel interpretar a mensagem."
            );
        }

        return message;
    }

    /**
     * Converte uma linha JSON recebida por TCP.
     */
    public static ChatMessage deserializeLine(String jsonLine)
            throws JsonProcessingException {

        if (jsonLine == null) {
            throw new IllegalArgumentException(
                    "A linha recebida e nula."
            );
        }

        String json = jsonLine.strip();

        return deserialize(json);
    }
}
