package pacote.database;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import protocolo.ChatMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Data Access Object responsavel pela persistencia das mensagens.
 * Utiliza a tabela mensagens definida no schema chat_distribuido.
 */
public final class MensagemDAO {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SQL_INSERIR = """
            INSERT INTO mensagens (
                message_id,
                message_type,
                sender,
                recipient,
                content,
                mode,
                color,
                avatar,
                sticker,
                sent_at,
                metadata,
                rendered_html
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_BUSCAR_POR_ID = """
            SELECT message_type, sender, recipient, content, mode, color,
                   avatar, sticker, sent_at, metadata, rendered_html
              FROM mensagens
             WHERE message_id = ?
            """;

    private static final String SQL_HISTORICO = """
            SELECT rendered_html
              FROM mensagens
             ORDER BY sent_at ASC, created_at ASC, message_id ASC
            """;

    private static final String SQL_CONTAR =
            "SELECT COUNT(*) FROM mensagens";

    /**
     * Persiste uma mensagem ja validada e renderizada pelo middleware.
     * O HTML e armazenado para preservar o formato consumido pelos clientes atuais.
     */
    public void salvar(ChatMessage mensagem, String renderedHtml)
            throws SQLException {

        Objects.requireNonNull(mensagem, "A mensagem nao pode ser nula.");

        String id = exigirTexto(mensagem.id(), "message_id");
        if (id.length() > 36) {
            throw new IllegalArgumentException(
                    "message_id ultrapassa o limite de 36 caracteres."
            );
        }

        if (mensagem.type() == null) {
            throw new IllegalArgumentException(
                    "O tipo da mensagem e obrigatorio."
            );
        }

        String remetente = exigirTexto(mensagem.sender(), "sender");
        String destinatario = textoOuVazio(mensagem.recipient());
        String conteudo = textoOuVazio(mensagem.content());
        String modo = textoOuVazio(mensagem.mode());
        String cor = textoOuVazio(mensagem.color());
        String avatar = textoOuVazio(mensagem.avatar());
        String figurinha = textoOuVazio(mensagem.sticker());
        String html = textoOuVazio(renderedHtml);
        LocalDateTime enviadoEm = converterDataHoraUtc(mensagem.sentAt());
        String metadataJson = serializarMetadata(mensagem.metadata());

        try (Connection conexao = DatabaseConnection.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(SQL_INSERIR)) {

            comando.setString(1, id);
            comando.setString(2, mensagem.type().name());
            comando.setString(3, remetente);
            comando.setString(4, destinatario);
            comando.setString(5, conteudo);
            comando.setString(6, modo);
            comando.setString(7, cor);
            comando.setString(8, avatar);
            comando.setString(9, figurinha);
            comando.setObject(10, enviadoEm);
            comando.setString(11, metadataJson);
            comando.setString(12, html);

            comando.executeUpdate();
        }
    }


    /**
     * Persiste a mensagem se o identificador ainda nao existir.
     * Se o ID ja estiver presente, valida se o conteudo e identico e trata
     * a chamada como repeticao idempotente. Um ID reutilizado com outro
     * conteudo e rejeitado para evitar divergencia entre os bancos.
     *
     * @return true quando uma nova linha foi inserida; false quando ja existia.
     */
    public boolean salvarSeAusente(ChatMessage mensagem, String renderedHtml)
            throws SQLException {

        Objects.requireNonNull(mensagem, "A mensagem nao pode ser nula.");

        String id = exigirTexto(mensagem.id(), "message_id");
        if (id.length() > 36) {
            throw new IllegalArgumentException("message_id ultrapassa o limite de 36 caracteres.");
        }
        if (mensagem.type() == null) {
            throw new IllegalArgumentException("O tipo da mensagem e obrigatorio.");
        }

        String tipo = mensagem.type().name();
        String remetente = exigirTexto(mensagem.sender(), "sender");
        String destinatario = textoOuVazio(mensagem.recipient());
        String conteudo = textoOuVazio(mensagem.content());
        String modo = textoOuVazio(mensagem.mode());
        String cor = textoOuVazio(mensagem.color());
        String avatar = textoOuVazio(mensagem.avatar());
        String figurinha = textoOuVazio(mensagem.sticker());
        String html = textoOuVazio(renderedHtml);
        LocalDateTime enviadoEm = converterDataHoraUtc(mensagem.sentAt());
        String metadataJson = serializarMetadata(mensagem.metadata());

        try (Connection conexao = DatabaseConnection.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(SQL_INSERIR)) {

            comando.setString(1, id);
            comando.setString(2, tipo);
            comando.setString(3, remetente);
            comando.setString(4, destinatario);
            comando.setString(5, conteudo);
            comando.setString(6, modo);
            comando.setString(7, cor);
            comando.setString(8, avatar);
            comando.setString(9, figurinha);
            comando.setObject(10, enviadoEm);
            comando.setString(11, metadataJson);
            comando.setString(12, html);

            boolean inserida;
            try {
                comando.executeUpdate();
                inserida = true;
            } catch (SQLException erro) {
                // Duplicidade de chave primaria: valida se e a mesma mensagem.
                // Nao usa ON DUPLICATE KEY UPDATE para nao exigir permissao UPDATE.
                if (erro.getErrorCode() != 1062 && !"23000".equals(erro.getSQLState())) {
                    throw erro;
                }
                inserida = false;
            }

            validarRegistroExistente(
                    conexao, id, tipo, remetente, destinatario, conteudo,
                    modo, cor, avatar, figurinha, enviadoEm, metadataJson, html
            );
            return inserida;
        }
    }

    private void validarRegistroExistente(
            Connection conexao,
            String id,
            String tipo,
            String remetente,
            String destinatario,
            String conteudo,
            String modo,
            String cor,
            String avatar,
            String figurinha,
            LocalDateTime enviadoEm,
            String metadataJson,
            String html) throws SQLException {

        try (PreparedStatement comando = conexao.prepareStatement(SQL_BUSCAR_POR_ID)) {
            comando.setString(1, id);
            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) {
                    throw new SQLException("A mensagem nao foi encontrada apos a operacao de persistencia.");
                }

                boolean iguais =
                        Objects.equals(tipo, resultado.getString("message_type"))
                        && Objects.equals(remetente, resultado.getString("sender"))
                        && Objects.equals(destinatario, resultado.getString("recipient"))
                        && Objects.equals(conteudo, resultado.getString("content"))
                        && Objects.equals(modo, resultado.getString("mode"))
                        && Objects.equals(cor, resultado.getString("color"))
                        && Objects.equals(avatar, resultado.getString("avatar"))
                        && Objects.equals(figurinha, resultado.getString("sticker"))
                        && Objects.equals(enviadoEm, resultado.getObject("sent_at", LocalDateTime.class))
                        && Objects.equals(html, resultado.getString("rendered_html"));

                try {
                    JsonNode metadataEsperada = JSON.readTree(metadataJson);
                    JsonNode metadataArmazenada = JSON.readTree(resultado.getString("metadata"));
                    iguais = iguais && Objects.equals(metadataEsperada, metadataArmazenada);
                } catch (JsonProcessingException erro) {
                    throw new SQLException("Nao foi possivel comparar os metadados da mensagem existente.", erro);
                }

                if (!iguais) {
                    throw new SQLException(
                            "Conflito de message_id: o identificador ja existe com outro conteudo."
                    );
                }
            }
        }
    }

    /**
     * Recupera o HTML das mensagens na ordem cronologica.
     * O resultado pode ser unido e enviado no HISTORY_RESPONSE existente.
     */
    public List<String> buscarHistoricoHtml() throws SQLException {
        List<String> mensagens = new ArrayList<>();

        try (Connection conexao = DatabaseConnection.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(SQL_HISTORICO);
             ResultSet resultado = comando.executeQuery()) {

            while (resultado.next()) {
                mensagens.add(resultado.getString("rendered_html"));
            }
        }

        return List.copyOf(mensagens);
    }

    public long contarMensagens() throws SQLException {
        try (Connection conexao = DatabaseConnection.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(SQL_CONTAR);
             ResultSet resultado = comando.executeQuery()) {

            if (resultado.next()) {
                return resultado.getLong(1);
            }
        }

        return 0;
    }

    private static String serializarMetadata(Map<String, ?> metadata)
            throws SQLException {

        try {
            return JSON.writeValueAsString(
                    metadata == null ? Map.of() : metadata
            );
        } catch (JsonProcessingException erro) {
            throw new SQLException(
                    "Nao foi possivel serializar os metadados da mensagem.",
                    erro
            );
        }
    }

    private static LocalDateTime converterDataHoraUtc(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "sent_at e obrigatorio para persistir a mensagem."
            );
        }

        try {
            return OffsetDateTime.parse(valor)
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (DateTimeParseException erroComFuso) {
            try {
                // Compatibilidade com valores ISO locais sem offset.
                return LocalDateTime.parse(valor);
            } catch (DateTimeParseException erroLocal) {
                erroLocal.addSuppressed(erroComFuso);
                throw new IllegalArgumentException(
                        "Formato de sent_at invalido: " + valor,
                        erroLocal
                );
            }
        }
    }

    private static String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "O campo " + campo + " e obrigatorio."
            );
        }
        return valor;
    }

    private static String textoOuVazio(String valor) {
        return valor == null ? "" : valor;
    }
}
