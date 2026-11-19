
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

    // Canais seguros
    public static final int PortaRecepcaoJsonTLS = 6664;
    public static final int PortaWebTLS = 8443;

    // URLs HTTPS dos servicos centralizados
    public static final String URL_FIGURINHAS =
            "https://" + Servidor + ":" + PortaWebTLS
            + "/figurinhas/";

    public static final String URL_API_STICKERS =
            "https://" + Servidor + ":" + PortaWebTLS
            + "/api/stickers";

    public static final String URL_COLETOR_LOGS =
            "https://" + Servidor + ":" + PortaWebTLS
            + "/api/logs";

    /**
     * Gera a URL HTTPS de um avatar armazenado no middleware.
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
