
package pacote;

import java.io.IOException;
import java.io.InputStream;

import java.net.InetSocketAddress;
import java.net.Socket;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

public final class ConfiguracaoTLSCliente {

    private static volatile SSLContext contexto;

    private ConfiguracaoTLSCliente() {
    }

    private static SSLContext obterContexto()
            throws GeneralSecurityException, IOException {

        if (contexto != null) {
            return contexto;
        }

        synchronized (ConfiguracaoTLSCliente.class) {

            if (contexto != null) {
                return contexto;
            }

            try (InputStream entrada =
                    ConfiguracaoTLSCliente.class.getResourceAsStream(
                            "/certificados/chat-root-ca.cer"
                    )) {

                if (entrada == null) {
                    throw new IOException(
                            "Certificado raiz nao encontrado nos recursos."
                    );
                }

                CertificateFactory fabrica =
                        CertificateFactory.getInstance("X.509");

                X509Certificate certificado =
                        (X509Certificate) fabrica.generateCertificate(
                                entrada
                        );

                KeyStore confiaveis =
                        KeyStore.getInstance(KeyStore.getDefaultType());

                confiaveis.load(null);

                confiaveis.setCertificateEntry(
                        "chat-root-ca",
                        certificado
                );

                TrustManagerFactory gerenciador =
                        TrustManagerFactory.getInstance(
                                TrustManagerFactory.getDefaultAlgorithm()
                        );

                gerenciador.init(confiaveis);

                SSLContext novoContexto =
                        SSLContext.getInstance("TLS");

                novoContexto.init(
                        null,
                        gerenciador.getTrustManagers(),
                        new SecureRandom()
                );

                // Disponibiliza o contexto TLS globalmente.
                SSLContext.setDefault(novoContexto);

                // IMPORTANTE:
                // Configura explicitamente as conexoes HTTPS.
                // Isso inclui HttpsURLConnection e carregamento
                // de imagens por URL.openConnection().
                HttpsURLConnection.setDefaultSSLSocketFactory(
                        novoContexto.getSocketFactory()
                );

                contexto = novoContexto;

                return contexto;
            }
        }
    }

    /**
     * Inicializa a confianca TLS antes do carregamento
     * de imagens e demais recursos HTTPS.
     */
    public static void inicializar() throws IOException {

        try {

            obterContexto();

        } catch (GeneralSecurityException erro) {

            throw new IOException(
                    "Nao foi possivel inicializar a configuracao TLS.",
                    erro
            );
        }
    }

    /**
     * Abre uma conexao TLS com validacao da cadeia
     * e do nome/IP do servidor.
     */
    public static SSLSocket conectar(
            String host,
            int porta) throws IOException {

        Socket conexaoBase = new Socket();

        try {

            conexaoBase.connect(
                    new InetSocketAddress(host, porta),
                    5000
            );

            SSLContext sslContexto = obterContexto();

            SSLSocket socket = (SSLSocket)
                    sslContexto.getSocketFactory().createSocket(
                            conexaoBase,
                            host,
                            porta,
                            true
                    );

            socket.setSoTimeout(10000);

            socket.setEnabledProtocols(
                    new String[]{"TLSv1.3", "TLSv1.2"}
            );

            SSLParameters parametros = socket.getSSLParameters();

            // Valida o nome DNS ou IP contra o certificado.
            parametros.setEndpointIdentificationAlgorithm("HTTPS");

            socket.setSSLParameters(parametros);

            socket.startHandshake();

            return socket;

        } catch (GeneralSecurityException erro) {

            try {
                conexaoBase.close();
            } catch (IOException ignorado) {
            }

            throw new IOException(
                    "Falha ao validar o certificado TLS do middleware.",
                    erro
            );

        } catch (IOException erro) {

            try {
                conexaoBase.close();
            } catch (IOException ignorado) {
            }

            throw erro;
        }
    }
}
