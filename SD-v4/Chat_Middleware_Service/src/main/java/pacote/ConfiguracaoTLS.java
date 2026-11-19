
package pacote;

import java.io.IOException;
import java.io.InputStream;

import java.nio.file.Files;
import java.nio.file.Path;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;

import java.util.Arrays;
import java.util.Properties;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

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
}
