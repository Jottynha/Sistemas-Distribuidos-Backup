
package pacote;

import java.io.IOException;
import java.io.InputStream;

import java.nio.file.Files;
import java.nio.file.Path;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import java.util.Arrays;
import java.util.Properties;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public final class ConfiguracaoTLS {

    private ConfiguracaoTLS() {
    }

    public static SSLContext criarContextoServidor()
            throws IOException, GeneralSecurityException {

        Path arquivoConfiguracao =
                Util.DiretorioDados.resolve("tls.properties");

        if (!Files.isRegularFile(arquivoConfiguracao)) {
            throw new IOException(
                    "Arquivo tls.properties nao encontrado: "
                    + arquivoConfiguracao
            );
        }

        Properties propriedades = new Properties();

        try (InputStream entrada =
                Files.newInputStream(arquivoConfiguracao)) {

            propriedades.load(entrada);
        }

        String caminhoKeystore =
                propriedades.getProperty("keystore.path");

        String senhaKeystore =
                propriedades.getProperty("keystore.password");

        if (caminhoKeystore == null
                || caminhoKeystore.isBlank()
                || senhaKeystore == null
                || senhaKeystore.isBlank()) {

            throw new IOException(
                    "Configuracao TLS incompleta."
            );
        }

        Path diretorioDados =
                Util.DiretorioDados.toAbsolutePath().normalize();

        Path arquivoKeystore =
                diretorioDados.resolve(caminhoKeystore).normalize();

        if (!arquivoKeystore.startsWith(diretorioDados)) {
            throw new IOException(
                    "Caminho do keystore fora do diretorio de dados."
            );
        }

        if (!Files.isRegularFile(arquivoKeystore)) {
            throw new IOException(
                    "Keystore nao encontrado: " + arquivoKeystore
            );
        }

        char[] senha = senhaKeystore.toCharArray();

        try {

            KeyStore keystore =
                    KeyStore.getInstance("PKCS12");

            try (InputStream entrada =
                    Files.newInputStream(arquivoKeystore)) {

                keystore.load(entrada, senha);
            }

            KeyManagerFactory gerenciador =
                    KeyManagerFactory.getInstance(
                            KeyManagerFactory.getDefaultAlgorithm()
                    );

            gerenciador.init(keystore, senha);

            SSLContext contexto =
                    SSLContext.getInstance("TLS");

            contexto.init(
                    gerenciador.getKeyManagers(),
                    null,
                    new SecureRandom()
            );

            return contexto;

        } finally {
            Arrays.fill(senha, '\0');
        }
    }
    /**
     * Cria um contexto TLS de cliente confiando exclusivamente na CA publica
     * indicada. A verificacao do hostname deve ser ativada no SSLSocket que
     * utilizar este contexto.
     */
    public static SSLContext criarContextoCliente(Path arquivoCa)
            throws IOException, GeneralSecurityException {

        if (arquivoCa == null || !Files.isRegularFile(arquivoCa)) {
            throw new IOException("Certificado raiz publico nao encontrado: " + arquivoCa);
        }

        X509Certificate certificado;
        try (InputStream entrada = Files.newInputStream(arquivoCa)) {
            CertificateFactory fabrica = CertificateFactory.getInstance("X.509");
            certificado = (X509Certificate) fabrica.generateCertificate(entrada);
        }

        certificado.checkValidity();
        if (certificado.getBasicConstraints() < 0) {
            throw new GeneralSecurityException("O certificado informado nao e uma CA.");
        }

        KeyStore confiaveis = KeyStore.getInstance(KeyStore.getDefaultType());
        confiaveis.load(null, null);
        confiaveis.setCertificateEntry("chat-root-ca", certificado);

        TrustManagerFactory gerenciador = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
        );
        gerenciador.init(confiaveis);

        SSLContext contexto = SSLContext.getInstance("TLS");
        contexto.init(null, gerenciador.getTrustManagers(), new SecureRandom());
        return contexto;
    }

}
