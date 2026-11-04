
package pacote;

public class Util {
    // Dados do usuario
    public static String nickname = "";
    public static String cor = "";
    public static String avatar = "";
    // Endereco do middleware
    public static final String Servidor =
            System.getProperty(
                    "chat.server.host",
                    System.getenv().getOrDefault(
                            "CHAT_SERVER_HOST",
                            "127.0.0.1"
                    )
            );
    // Portas TCP
    public static final int PortaEnvioDesktop = 6661;
    public static final int PortaRecepcaoDesktop = 6662;
    // Portas HTTP
    public static final int PortaWeb = 8081;
    public static final int PortaLogs = 8082;
    // URLs dos servicos
    public static final String URL_FIGURINHAS =
            "http://" + Servidor + ":" + PortaWeb + "/figurinhas/";
    public static final String URL_API_STICKERS =
            "http://" + Servidor + ":" + PortaWeb + "/api/stickers";
    public static final String URL_COLETOR_LOGS =
            "http://" + Servidor + ":" + PortaLogs + "/api/logs";
    /**
     * Gera a URL de um avatar armazenado no servidor.
     */
    public static String obterURLAvatar() {
        if (avatar == null || avatar.isBlank()) {
            return "";
        }
        String nomeArquivo = avatar.substring(
                avatar.lastIndexOf('/') + 1
        );
        return URL_FIGURINHAS
                + "avatars/"
                + java.net.URLEncoder.encode(
                        nomeArquivo,
                        java.nio.charset.StandardCharsets.UTF_8
                ).replace("+", "%20");
    }
}
