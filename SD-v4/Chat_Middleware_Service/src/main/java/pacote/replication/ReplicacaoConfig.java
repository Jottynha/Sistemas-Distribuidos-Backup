package pacote.replication;

import java.io.IOException;
import java.util.Locale;
import java.nio.file.Path;
import pacote.Util;

/** Configuracao local da replicacao do middleware principal para o reserva. */
public record ReplicacaoConfig(
        String host,
        int port,
        String token,
        Path caFile,
        int connectTimeoutMs,
        int readTimeoutMs) {

    public static boolean habilitada() {
        String valor = System.getenv("CHAT_REPLICATION_ENABLED");
        if (valor == null || valor.isBlank()) {
            return false;
        }
        return switch (valor.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "sim" -> true;
            case "false", "0", "no", "nao", "não" -> false;
            default -> throw new IllegalArgumentException(
                    "CHAT_REPLICATION_ENABLED deve ser true ou false."
            );
        };
    }

    public static ReplicacaoConfig carregarCliente() throws IOException {
        String host = obrigatoria("CHAT_REPLICATION_HOST").trim();
        if (host.isBlank() || host.contains("/") || host.contains("@")
                || host.contains("?") || host.contains("#")) {
            throw new IOException("CHAT_REPLICATION_HOST contem um endereco invalido.");
        }

        int port = inteiro("CHAT_REPLICATION_PORT", 6666);
        int connectTimeout = inteiro("CHAT_REPLICATION_CONNECT_TIMEOUT_MS", 5000);
        int readTimeout = inteiro("CHAT_REPLICATION_READ_TIMEOUT_MS", 8000);
        if (port < 1 || port > 65535) {
            throw new IOException("CHAT_REPLICATION_PORT deve estar entre 1 e 65535.");
        }
        if (connectTimeout < 1 || readTimeout < 1) {
            throw new IOException("Os timeouts de replicacao devem ser positivos.");
        }

        String token = obrigatoria("CHAT_REPLICATION_TOKEN");
        Path caFile = Util.localizarCertificadoRaizPublico();
        return new ReplicacaoConfig(host, port, token, caFile, connectTimeout, readTimeout);
    }

    public static String tokenReceptor() throws IOException {
        return obrigatoria("CHAT_REPLICATION_TOKEN");
    }

    public static int portaReceptor() throws IOException {
        int port = inteiro("CHAT_REPLICATION_PORT", 6666);
        if (port < 1 || port > 65535) {
            throw new IOException("CHAT_REPLICATION_PORT deve estar entre 1 e 65535.");
        }
        return port;
    }

    private static String obrigatoria(String nome) throws IOException {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) {
            throw new IOException("Variavel de ambiente obrigatoria nao configurada: " + nome);
        }
        return valor;
    }

    private static int inteiro(String nome, int padrao) throws IOException {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) {
            return padrao;
        }
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException erro) {
            throw new IOException(nome + " deve ser um numero inteiro.", erro);
        }
    }
}
