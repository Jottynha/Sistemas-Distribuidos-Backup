
package pacote;

import protocolo.ChatMessage;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RenderizadorMensagemHtml {

    private static final Pattern NOME_IMAGEM = Pattern.compile(
            "(?i)[a-z0-9][a-z0-9._-]{0,100}\\.(png|gif|jpg|jpeg|webp)"
    );

    private static final Pattern MARCADOR_STICKER = Pattern.compile(
            "\\(sticker:([^)]+)\\)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Set<String> CORES_PERMITIDAS = Set.of(
            "Navy",
            "Tomato",
            "MediumSeaGreen"
    );

    private RenderizadorMensagemHtml() {
    }

    public static String renderizar(ChatMessage mensagem) {

        String remetente = escaparHtml(mensagem.sender());

        String cor = mensagem.color();

        if (cor == null
                || (!cor.matches("^#[0-9a-fA-F]{6}$")
                && !CORES_PERMITIDAS.contains(cor))) {
            cor = "#333333";
        }

        String modo = mensagem.mode() == null
                ? ""
                : mensagem.mode().toUpperCase(Locale.ROOT);

        StringBuilder html = new StringBuilder();

        html.append("<div class=\"mensagem\">");

        // Avatar.
        String avatar = validarNomeImagem(mensagem.avatar());

        if (!avatar.isBlank()) {
            html.append("<img src=\"/figurinhas/avatars/")
                    .append(avatar)
                    .append("\" width=\"30\" height=\"30\" ")
                    .append("alt=\"Avatar\" ")
                    .append("style=\"vertical-align:middle;\"> ");
        }

        // Remetente.
        html.append("<strong style=\"color:")
                .append(cor)
                .append("\">")
                .append(remetente)
                .append("</strong>: ");

        if (!modo.isBlank() && !"FALA".equals(modo)) {
            html.append("<b>")
                    .append(escaparHtml(modo))
                    .append(": </b>");
        }

        // Texto e figurinhas inseridas no campo de mensagem.
        html.append(renderizarConteudo(mensagem.content()));

        // Figurinha enviada no campo estruturado.
        String sticker = validarNomeImagem(mensagem.sticker());

        if (!sticker.isBlank()) {
            html.append(" <img src=\"/figurinhas/")
                    .append(sticker)
                    .append("\" width=\"72\" height=\"72\" ")
                    .append("alt=\"Figurinha\" ")
                    .append("style=\"vertical-align:middle;\">");
        }

        html.append("</div><br>");

        return html.toString();
    }

    private static String renderizarConteudo(String texto) {

        if (texto == null || texto.isEmpty()) {
            return "";
        }

        Matcher matcher = MARCADOR_STICKER.matcher(texto);

        StringBuilder html = new StringBuilder();

        int inicio = 0;

        while (matcher.find()) {

            // Texto anterior ao marcador.
            html.append(escaparHtmlComQuebras(
                    texto.substring(inicio, matcher.start())
            ));

            String nomeArquivo = validarNomeImagem(
                    matcher.group(1).trim()
            );

            if (nomeArquivo.isBlank()) {

                html.append(escaparHtml(matcher.group()));

            } else {

                html.append(" <img src=\"/figurinhas/")
                        .append(nomeArquivo)
                        .append("\" width=\"72\" height=\"72\" ")
                        .append("alt=\"Figurinha\" ")
                        .append("style=\"vertical-align:middle;\">");
            }

            inicio = matcher.end();
        }

        // Texto restante.
        html.append(escaparHtmlComQuebras(
                texto.substring(inicio)
        ));

        return html.toString();
    }

    private static String validarNomeImagem(String nome) {

        if (nome == null || nome.isBlank()) {
            return "";
        }

        // Aceita tanto nome de arquivo quanto URL/caminho antigo.
        String candidato = nome.trim();

        int ultimaBarra = candidato.lastIndexOf('/');

        if (ultimaBarra >= 0) {
            candidato = candidato.substring(ultimaBarra + 1);
        }

        if (candidato.contains("..")
                || !NOME_IMAGEM.matcher(candidato).matches()) {
            return "";
        }

        return candidato;
    }

    private static String escaparHtmlComQuebras(String texto) {

        return escaparHtml(texto)
                .replace("\r\n", "<br>")
                .replace("\r", "<br>")
                .replace("\n", "<br>");
    }

    private static String escaparHtml(String texto) {

        if (texto == null) {
            return "";
        }

        return texto
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
