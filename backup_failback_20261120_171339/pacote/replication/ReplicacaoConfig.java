package pacote.replication;

import java.io.IOException;
import java.util.Locale;
import java.nio.file.Path;
import pacote.Util;

/** Configuracao local da replicacao e deteccao de falha do middleware. */
public record ReplicacaoConfig(
        String host,
        int port,
        String token,
        Path caFile,
        int connectTimeoutMs,
        int readTimeoutMs) {

    public static boolean habilitada() {
        String valor = System.getenv("CHAT_REPLICATION_ENABLED");
        if (valor == null || valor.isBlank()) return false;
        return switch (valor.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "sim" -> true;
            case "false", "0", "no", "nao", "não" -> false;
            default -> throw new IllegalArgumentException("CHAT_REPLICATION_ENABLED deve ser true ou false.");
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
        if (port < 1 || port > 65535 || connectTimeout < 1 || readTimeout < 1) {
            throw new IOException("Porta e timeouts de replicacao invalidos.");
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
        if (port < 1 || port > 65535) throw new IOException("CHAT_REPLICATION_PORT deve estar entre 1 e 65535.");
        return port;
    }

    /** Periodo entre heartbeats enviados pelo principal. */
    public static long intervaloHeartbeatMs() throws IOException {
        return inteiro("CHAT_FAILOVER_HEARTBEAT_INTERVAL_MS", 2000);
    }

    /** Tempo maximo sem lease confirmado antes de o principal se auto-fenciar. */
    public static long leasePrincipalMs() throws IOException {
        return inteiro("CHAT_FAILOVER_PRIMARY_LEASE_MS", 7000);
    }

    /** Tempo sem heartbeat que leva o reserva a ser promovido. */
    public static long timeoutPromocaoMs() throws IOException {
        return inteiro("CHAT_FAILOVER_TIMEOUT_MS", 10000);
    }

    /** Janela inicial para permitir que o principal se anuncie ao reserva. */
    public static long graceInicialMs() throws IOException {
        return inteiro("CHAT_FAILOVER_STARTUP_GRACE_MS", 20000);
    }

    /** Prazo maximo para o principal antigo concluir a sincronizacao de failback. */
    public static long timeoutReintegracaoMs() throws IOException {
        return inteiro("CHAT_FAILBACK_TIMEOUT_MS", 60000);
    }

    private static String obrigatoria(String nome) throws IOException {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) throw new IOException("Variavel de ambiente obrigatoria nao configurada: " + nome);
        return valor;
    }

    private static int inteiro(String nome, int padrao) throws IOException {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) return padrao;
        try {
            int numero = Integer.parseInt(valor.trim());
            if (numero < 1) throw new NumberFormatException("valor deve ser positivo");
            return numero;
        } catch (NumberFormatException erro) {
            throw new IOException(nome + " deve ser um inteiro positivo.", erro);
        }
    }
}
