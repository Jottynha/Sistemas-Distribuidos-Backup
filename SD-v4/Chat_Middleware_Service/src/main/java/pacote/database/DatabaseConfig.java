package pacote.database;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Carrega as configuracoes do MySQL exclusivamente por variaveis de ambiente.
 * Nenhuma credencial deve ser armazenada no codigo-fonte ou no Git.
 */
public final class DatabaseConfig {

    private static final Pattern DATABASE_NAME_PATTERN =
            Pattern.compile("[A-Za-z0-9_]+(?:[A-Za-z0-9_$]*)");

    private static final Set<String> MODOS_SSL_PERMITIDOS = Set.of(
            "PREFERRED",
            "REQUIRED",
            "VERIFY_CA",
            "VERIFY_IDENTITY"
    );

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String sslMode;

    private DatabaseConfig(
            String host,
            int port,
            String database,
            String username,
            String password,
            String sslMode) {

        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.sslMode = sslMode;
    }

    public static DatabaseConfig carregar() {
        String host = valor("CHAT_DB_HOST", "127.0.0.1").trim();
        String portTexto = valor("CHAT_DB_PORT", "3306").trim();
        String database = valor("CHAT_DB_NAME", "chat_distribuido").trim();
        String username = obrigatorio("CHAT_DB_USER").trim();
        String password = obrigatorio("CHAT_DB_PASSWORD");
        String sslMode = valor("CHAT_DB_SSL_MODE", "PREFERRED")
                .trim()
                .toUpperCase(Locale.ROOT);

        if (host.isBlank()
                || host.contains("/")
                || host.contains("?")
                || host.contains("#")
                || host.contains("@")) {
            throw new IllegalArgumentException(
                    "CHAT_DB_HOST contem um endereco invalido."
            );
        }

        final int port;
        try {
            port = Integer.parseInt(portTexto);
        } catch (NumberFormatException erro) {
            throw new IllegalArgumentException(
                    "CHAT_DB_PORT deve ser um numero inteiro.", erro
            );
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                    "CHAT_DB_PORT deve estar entre 1 e 65535."
            );
        }

        if (!DATABASE_NAME_PATTERN.matcher(database).matches()) {
            throw new IllegalArgumentException(
                    "CHAT_DB_NAME contem caracteres nao permitidos."
            );
        }

        if (username.isBlank()) {
            throw new IllegalArgumentException(
                    "CHAT_DB_USER nao pode ficar vazio."
            );
        }

        if (password.isEmpty()) {
            throw new IllegalArgumentException(
                    "CHAT_DB_PASSWORD nao pode ficar vazio."
            );
        }

        if (!MODOS_SSL_PERMITIDOS.contains(sslMode)) {
            throw new IllegalArgumentException(
                    "CHAT_DB_SSL_MODE deve ser PREFERRED, REQUIRED, "
                    + "VERIFY_CA ou VERIFY_IDENTITY."
            );
        }

        return new DatabaseConfig(
                host,
                port,
                database,
                username,
                password,
                sslMode
        );
    }

    private static String valor(String nome, String padrao) {
        String valor = System.getenv(nome);
        return valor == null || valor.isBlank() ? padrao : valor;
    }

    private static String obrigatorio(String nome) {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(
                    "Variavel de ambiente obrigatoria nao configurada: " + nome
            );
        }
        return valor;
    }

    public String jdbcUrl() {
        String hostUrl = host;
        if (hostUrl.contains(":") && !hostUrl.startsWith("[")) {
            hostUrl = "[" + hostUrl + "]";
        }

        return "jdbc:mysql://" + hostUrl + ":" + port + "/" + database
                + "?connectionTimeZone=UTC"
                + "&forceConnectionTimeZoneToSession=true"
                + "&characterEncoding=UTF-8"
                + "&sslMode=" + sslMode
                + "&connectTimeout=5000"
                + "&socketTimeout=15000"
                + "&tcpKeepAlive=true";
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    @Override
    public String toString() {
        return "DatabaseConfig{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", database='" + database + '\'' +
                ", username='" + username + '\'' +
                ", password='[OCULTA]'" +
                ", sslMode='" + sslMode + '\'' +
                '}';
    }
}
