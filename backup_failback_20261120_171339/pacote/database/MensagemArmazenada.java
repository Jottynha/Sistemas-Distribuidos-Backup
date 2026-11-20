package pacote.database;

import java.util.Objects;
import protocolo.ChatMessage;

/** Registro completo transferido entre os bancos dos middlewares. */
public record MensagemArmazenada(ChatMessage mensagem, String renderedHtml) {
    public MensagemArmazenada {
        Objects.requireNonNull(mensagem, "A mensagem armazenada e obrigatoria.");
        if (renderedHtml == null) {
            renderedHtml = "";
        }
    }
}
