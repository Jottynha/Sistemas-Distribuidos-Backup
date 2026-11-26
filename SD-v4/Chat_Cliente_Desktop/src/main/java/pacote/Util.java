
package pacote;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;

/**
 * Configuracao de endpoints primario/reserva para failover
 * transparente do cliente Desktop.
 */
public class Util {

    public static String nickname = "";
    public static String cor = "";
    public static String avatar = "";

    private static final String HOST_PRIMARIO =
            valor("chat.server.host", "CHAT_SERVER_HOST", "10.142.98.144");

    private static final String HOST_RESERVA =
            valor("chat.server.backupHost", "CHAT_SERVER_BACKUP_HOST", "");

    public static volatile String Servidor = HOST_PRIMARIO;

    public static final int PortaRecepcaoJsonTLS =
            inteiro("CHAT_SERVER_DESKTOP_PORT", 6664);

    public static final int PortaWebTLS =
            inteiro("CHAT_SERVER_WEB_PORT", 8443);

    public static volatile String URL_FIGURINHAS;
    public static volatile String URL_API_STICKERS;
    public static volatile String URL_COLETOR_LOGS;
    public static volatile String URL_PUBLICIDADE;

    static {
        atualizarUrls();
    }

    private Util() {
    }

    /**
     * Tenta o middleware principal e depois o reserva.
     * O ultimo host funcional passa a ser o preferencial.
     */
    public static SSLSocket conectarMiddleware(int porta)
            throws IOException {

        IOException ultima = null;

        for (String host : obterCandidatos()) {
            try {
                return conectarMiddleware(host, porta);

            } catch (IOException erro) {
                ultima = erro;
            }
        }

        throw new IOException(
                "Nenhum middleware configurado esta acessivel (primario/reserva).",
                ultima
        );
    }

    /**
     * Abre TLS diretamente para um host especifico.
     */
    public static SSLSocket conectarMiddleware(String host, int porta)
            throws IOException {

        if (host == null || host.isBlank()) {
            throw new IOException("Endereco do middleware vazio.");
        }

        SSLSocket socket =
                ConfiguracaoTLSCliente.conectar(host.trim(), porta);

        selecionarServidor(host);

        return socket;
    }

    /**
     * Executa uma requisicao GET HTTPS no principal e tenta
     * o reserva quando a conexao falha.
     *
     * A conexao retornada ja teve seu status HTTP consultado.
     */
    public static HttpsURLConnection abrirConexaoHttps(String caminho)
            throws IOException {

        String path = normalizarCaminho(caminho);

        long limite = System.nanoTime()
                + java.util.concurrent.TimeUnit.SECONDS.toNanos(25);

        IOException ultima = null;

        do {
            for (String host : obterCandidatos()) {

                HttpsURLConnection conexao = null;

                try {
                    conexao = (HttpsURLConnection)
                            new URL(urlBase(host) + path).openConnection();

                    conexao.setRequestMethod("GET");
                    conexao.setConnectTimeout(3000);
                    conexao.setReadTimeout(8000);

                    int status = conexao.getResponseCode();

                    if (status >= 500
                            || status == 408
                            || status == 425) {

                        throw new IOException(
                                "Middleware " + host
                                + " retornou HTTP " + status
                        );
                    }

                    selecionarServidor(host);

                    return conexao;

                } catch (IOException erro) {
                    ultima = erro;

                    if (conexao != null) {
                        conexao.disconnect();
                    }
                }
            }

            if (System.nanoTime() >= limite) {
                break;
            }

            aguardarNovaTentativa();

        } while (System.nanoTime() < limite);

        throw new IOException(
                "Nenhum endpoint HTTPS de middleware respondeu durante a janela de failover.",
                ultima
        );
    }

    /**
     * Carrega uma imagem de publicidade utilizando failover HTTPS.
     *
     * O nome do arquivo e validado antes de montar a requisicao.
     */
    public static HttpsURLConnection abrirPublicidade(String nomeArquivo)
            throws IOException {

        if (nomeArquivo == null
                || !nomeArquivo.matches(
                        "[A-Za-z0-9._-]{1,180}\\.(png|gif|jpg|jpeg|webp)"
                )
                || nomeArquivo.contains("..")) {

            throw new IOException(
                    "Nome de arquivo de publicidade invalido."
            );
        }

        String nomeCodificado = URLEncoder.encode(
                nomeArquivo,
                StandardCharsets.UTF_8
        ).replace("+", "%20");

        return abrirConexaoHttps(
                "/publicidade/" + nomeCodificado
        );
    }

    /**
     * Envia logs por POST, repetindo nos dois middlewares
     * durante a promocao do reserva.
     */
    public static int postarHttps(
            String caminho,
            byte[] dados,
            String contentType
    ) throws IOException {

        if (dados == null) {
            throw new IOException("Dados da requisicao nao informados.");
        }

        String path = normalizarCaminho(caminho);

        long limite = System.nanoTime()
                + java.util.concurrent.TimeUnit.SECONDS.toNanos(25);

        IOException ultima = null;

        do {
            for (String host : obterCandidatos()) {

                HttpsURLConnection conexao = null;

                try {
                    conexao = (HttpsURLConnection)
                            new URL(urlBase(host) + path).openConnection();

                    conexao.setRequestMethod("POST");
                    conexao.setDoOutput(true);
                    conexao.setConnectTimeout(3000);
                    conexao.setReadTimeout(8000);

                    conexao.setRequestProperty(
                            "Content-Type",
                            contentType == null
                                    ? "application/octet-stream"
                                    : contentType
                    );

                    conexao.setFixedLengthStreamingMode(dados.length);

                    try (var saida = conexao.getOutputStream()) {
                        saida.write(dados);
                        saida.flush();
                    }

                    int status = conexao.getResponseCode();

                    if (status >= 500
                            || status == 408
                            || status == 425) {

                        throw new IOException(
                                "Middleware " + host
                                + " retornou HTTP " + status
                        );
                    }

                    selecionarServidor(host);

                    return status;

                } catch (IOException erro) {
                    ultima = erro;

                    if (conexao != null) {
                        conexao.disconnect();
                    }
                }
            }

            if (System.nanoTime() >= limite) {
                break;
            }

            aguardarNovaTentativa();

        } while (System.nanoTime() < limite);

        throw new IOException(
                "Nao foi possivel enviar a requisicao HTTPS a nenhum middleware durante o failover.",
                ultima
        );
    }

    /**
     * Gera a URL HTTPS do avatar armazenado no middleware
     * atualmente selecionado.
     */
    public static String obterURLAvatar() {

        if (avatar == null || avatar.isBlank()) {
            return "";
        }

        String nomeArquivo =
                avatar.substring(avatar.lastIndexOf('/') + 1);

        return URL_FIGURINHAS
                + "avatars/"
                + URLEncoder.encode(
                        nomeArquivo,
                        StandardCharsets.UTF_8
                ).replace("+", "%20");
    }

    /**
     * Atualiza o middleware preferencial e todas as URLs.
     */
    public static synchronized void selecionarServidor(String host) {

        if (host == null || host.isBlank()) {
            return;
        }

        Servidor = host.trim();

        atualizarUrls();
    }

    /**
     * Retorna os hosts na ordem de tentativa:
     * ultimo funcional, principal e reserva.
     */
    public static List<String> obterCandidatos() {

        LinkedHashSet<String> hosts = new LinkedHashSet<>();

        adicionar(hosts, Servidor);
        adicionar(hosts, HOST_PRIMARIO);
        adicionar(hosts, HOST_RESERVA);

        return new ArrayList<>(hosts);
    }

    private static void adicionar(
            LinkedHashSet<String> hosts,
            String host
    ) {

        if (host != null && !host.isBlank()) {
            hosts.add(host.trim());
        }
    }

    private static synchronized void atualizarUrls() {

        URL_FIGURINHAS =
                urlBase(Servidor) + "/figurinhas/";

        URL_API_STICKERS =
                urlBase(Servidor) + "/api/stickers";

        URL_COLETOR_LOGS =
                urlBase(Servidor) + "/api/logs";

        URL_PUBLICIDADE =
                urlBase(Servidor) + "/publicidade/";
    }

    private static String normalizarCaminho(String caminho) {

        String path = caminho == null ? "/" : caminho.trim();

        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        return path;
    }

    private static String urlBase(String host) {

        String formatado = host.contains(":")
                && !host.startsWith("[")
                ? "[" + host + "]"
                : host;

        return "https://" + formatado + ":" + PortaWebTLS;
    }

    private static void aguardarNovaTentativa()
            throws IOException {

        try {
            Thread.sleep(900);

        } catch (InterruptedException erro) {

            Thread.currentThread().interrupt();

            throw new IOException(
                    "Requisicao interrompida durante failover.",
                    erro
            );
        }
    }

    private static String valor(
            String propriedade,
            String variavel,
            String padrao
    ) {

        String resultado = System.getProperty(propriedade);

        if (resultado == null || resultado.isBlank()) {
            resultado = System.getenv(variavel);
        }

        return resultado == null || resultado.isBlank()
                ? padrao
                : resultado.trim();
    }

    private static int inteiro(String nome, int padrao) {

        String valor = System.getenv(nome);

        if (valor == null || valor.isBlank()) {
            return padrao;
        }

        try {
            int numero = Integer.parseInt(valor.trim());

            if (numero < 1 || numero > 65535) {
                throw new NumberFormatException();
            }

            return numero;

        } catch (NumberFormatException erro) {

            throw new ExceptionInInitializerError(
                    nome + " deve estar entre 1 e 65535."
            );
        }
    }
}
