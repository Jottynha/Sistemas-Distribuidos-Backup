package pacote;

import protocolo.ChatMessage;
import protocolo.JsonProtocol;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import java.io.IOException;
import java.net.InetSocketAddress;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import java.security.GeneralSecurityException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

public class Util {

    // =========================================================
    // 1. PORTAS
    // =========================================================

    // Portas antigas: mantidas apenas para compatibilidade
    // com as classes legadas que ainda existem no projeto.
    @Deprecated
    public static final int PortaEnvioDesktop = 6661;

    @Deprecated
    public static final int PortaRecepcaoDesktop = 6662;

    @Deprecated
    public static final int PortaRecepcaoJson = 6663;

    @Deprecated
    public static final int PortaWeb = 8081;

    @Deprecated
    public static final int PortaLogs = 8082;

    // Portas definitivas.
    public static final int PortaRecepcaoJsonTLS = 6664;
    public static final int PortaWebTLS = 8443;

    // =========================================================
    // 2. DIRETORIOS
    // =========================================================

    private static final Path DiretorioBase =
            localizarDiretorioBase();

    public static final Path DiretorioDados =
            DiretorioBase.resolve("data");

    public static final Path DiretorioLogs =
            DiretorioDados.resolve("logs");

    public static final Path DiretorioFigurinhas =
            DiretorioBase.resolve("figurinhas");

    public static final String PathRepDesktop =
            DiretorioDados.resolve(
                    "RepositorioDesktop.txt"
            ).toString();

    // =========================================================
    // 3. CONTROLE DOS SERVICOS DESKTOP
    // =========================================================

    // Os dois servidores abaixo sao legados e nao serao
    // mais inicializados.
    public static DesktopRecepcaoThread desktopRecepcaoThread;
    public static DesktopEnvioThread desktopEnvioThread;

    public static DesktopJsonRecepcaoThread desktopJsonRecepcaoThread;

    // =========================================================
    // 4. HISTORICO COMPARTILHADO
    // =========================================================

    private static final List<String> historicoMensagens =
            new ArrayList<>();

    private static final ReentrantReadWriteLock travaHistorico =
            new ReentrantReadWriteLock(true);

    private static boolean historicoInicializado = false;

    // =========================================================
    // 5. SERVIDOR HTTPS
    // =========================================================

    private static HttpsServer servidorWebTLS;

    private static ExecutorService executorWebTLS;

    // O coletor utiliza o mesmo servidor HTTPS.
    private static volatile boolean coletorLogsAtivo = false;

    // =========================================================
    // 6. CONTROLE DOS ARQUIVOS DE LOG
    // =========================================================

    private static final ConcurrentMap<Path, Object> travasArquivos =
            new ConcurrentHashMap<>();

    private static final DateTimeFormatter FORMATO_DATA =
            DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd HH:mm:ss"
            );

    private Util() {
    }

    // =========================================================
    // 7. LOCALIZACAO DO DIRETORIO BASE
    // =========================================================

    private static Path localizarDiretorioBase() {

        String configurado =
                System.getProperty("chat.server.baseDir");

        if (configurado == null || configurado.isBlank()) {
            configurado = System.getenv("CHAT_SERVER_BASE_DIR");
        }

        if (configurado != null && !configurado.isBlank()) {

            return Path.of(configurado)
                    .toAbsolutePath()
                    .normalize();
        }

        try {

            Path local = Path.of(
                    Util.class.getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            ).toAbsolutePath().normalize();

            // NetBeans/Maven:
            // .../Chat_Middleware_Service/target/classes
            if (Files.isDirectory(local)) {

                Path parent = local.getParent();

                if (local.getFileName() != null
                        && local.getFileName().toString().equals("classes")
                        && parent != null
                        && parent.getFileName() != null
                        && parent.getFileName().toString().equals("target")
                        && parent.getParent() != null) {

                    return parent.getParent();
                }

                return local;
            }

            // JAR:
            // .../Chat_Middleware_Service/target/aplicacao.jar
            Path parent = local.getParent();

            if (parent != null
                    && parent.getFileName() != null
                    && parent.getFileName().toString().equals("target")
                    && parent.getParent() != null) {

                return parent.getParent();
            }

            return parent != null
                    ? parent
                    : Path.of(System.getProperty("user.dir"));

        } catch (Exception erro) {

            System.err.println(
                    "Nao foi possivel localizar a pasta do middleware: "
                    + erro.getMessage()
            );

            return Path.of(
                    System.getProperty("user.dir")
            ).toAbsolutePath().normalize();
        }
    }

    // =========================================================
    // 8. HISTORICO EM RAM E TXT
    // =========================================================

    public static synchronized void inicializarRepositorio()
            throws IOException {

        if (historicoInicializado) {
            return;
        }

        Files.createDirectories(DiretorioDados);
        Files.createDirectories(DiretorioLogs);
        Files.createDirectories(DiretorioFigurinhas);

        Files.createDirectories(
                DiretorioFigurinhas.resolve("avatars")
        );

        System.out.println(
                "========== DIRETORIOS DO MIDDLEWARE =========="
        );

        System.out.println("Base: " + DiretorioBase);
        System.out.println("Dados: " + DiretorioDados);
        System.out.println("Logs: " + DiretorioLogs);
        System.out.println("Figurinhas: " + DiretorioFigurinhas);
        System.out.println("Historico: " + PathRepDesktop);

        System.out.println(
                "=============================================="
        );

        Path arquivoHistorico = Path.of(PathRepDesktop);

        Path arquivoAntigo = DiretorioBase.resolve(
                Path.of("C_", "Dados", "RepositorioDesktop.txt")
        );

        // Migra o historico antigo sem apagar o original.
        if (Files.notExists(arquivoHistorico)
                && Files.isRegularFile(arquivoAntigo)) {

            Files.copy(
                    arquivoAntigo,
                    arquivoHistorico,
                    StandardCopyOption.COPY_ATTRIBUTES
            );
        }

        if (Files.notExists(arquivoHistorico)) {
            Files.createFile(arquivoHistorico);
        }

        List<String> linhas = Files.readAllLines(
                arquivoHistorico,
                StandardCharsets.UTF_8
        );

        travaHistorico.writeLock().lock();

        try {

            historicoMensagens.clear();

            for (String linha : linhas) {

                if (linha != null && !linha.isBlank()) {
                    historicoMensagens.add(linha);
                }
            }

            historicoInicializado = true;

        } finally {
            travaHistorico.writeLock().unlock();
        }

        System.out.println(
                "Historico carregado: "
                + historicoMensagens.size()
                + " mensagem(ns)."
        );
    }

    /**
     * Grava primeiro no TXT e depois atualiza a RAM.
     */
    public static void adicionarMensagem(String mensagem)
            throws IOException {

        inicializarRepositorio();

        if (mensagem == null || mensagem.isBlank()) {

            throw new IllegalArgumentException(
                    "A mensagem nao pode ser vazia."
            );
        }

        String conteudo = mensagem
                .replace("\r\n", "<br>")
                .replace('\r', '\n')
                .replace("\n", "<br>");

        byte[] bytes = (
                conteudo + System.lineSeparator()
        ).getBytes(StandardCharsets.UTF_8);

        travaHistorico.writeLock().lock();

        try {

            Path arquivo = Path.of(PathRepDesktop);

            try (FileChannel canal = FileChannel.open(
                    arquivo,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE)) {

                long tamanhoOriginal = canal.size();

                try {

                    // Corrige arquivos antigos sem quebra de linha final.
                    if (tamanhoOriginal > 0) {

                        ByteBuffer ultimoByte =
                                ByteBuffer.allocate(1);

                        canal.position(tamanhoOriginal - 1);
                        canal.read(ultimoByte);

                        byte ultimo = ultimoByte.array()[0];

                        if (ultimo != '\n' && ultimo != '\r') {

                            canal.position(tamanhoOriginal);

                            escreverTudo(
                                    canal,
                                    ByteBuffer.wrap(
                                            System.lineSeparator()
                                                    .getBytes(StandardCharsets.UTF_8)
                                    )
                            );
                        }
                    }

                    canal.position(canal.size());

                    escreverTudo(
                            canal,
                            ByteBuffer.wrap(bytes)
                    );

                    canal.force(true);

                } catch (IOException erro) {

                    // Tenta desfazer uma gravacao incompleta.
                    try {

                        canal.truncate(tamanhoOriginal);
                        canal.force(true);

                    } catch (IOException erroRollback) {

                        erro.addSuppressed(erroRollback);
                    }

                    throw erro;
                }
            }

            // Atualiza a memoria somente apos a persistencia.
            historicoMensagens.add(conteudo);

        } finally {
            travaHistorico.writeLock().unlock();
        }
    }

    private static void escreverTudo(
            FileChannel canal,
            ByteBuffer buffer) throws IOException {

        while (buffer.hasRemaining()) {
            canal.write(buffer);
        }
    }

    /**
     * Retorna uma copia do historico em memoria.
     */
    public static List<String> obterHistorico()
            throws IOException {

        inicializarRepositorio();

        travaHistorico.readLock().lock();

        try {
            return List.copyOf(historicoMensagens);

        } finally {
            travaHistorico.readLock().unlock();
        }
    }

    /**
     * Mantem o formato HTML utilizado pelo Desktop.
     */
    public static String obterHistoricoHtml()
            throws IOException {

        return String.join("", obterHistorico());
    }

    // =========================================================
    // 9. SERVIDOR DESKTOP: SOMENTE JSON TLS
    // =========================================================

    public static synchronized void iniciarDesktop()
            throws IOException {

        inicializarRepositorio();

        // Os servidores antigos das portas 6661 e 6662
        // nao sao mais inicializados.

        if (desktopJsonRecepcaoThread == null) {

            desktopJsonRecepcaoThread =
                    new DesktopJsonRecepcaoThread();

            Thread.ofVirtual()
                    .name("desktop-json-tls")
                    .start(desktopJsonRecepcaoThread);
        }

        System.out.println(
                "Servico Desktop seguro configurado na porta "
                + PortaRecepcaoJsonTLS
        );
    }

    public static synchronized void pararDesktop() {

        // Fecha qualquer instancia antiga que ainda exista.
        if (desktopRecepcaoThread != null) {

            desktopRecepcaoThread.fecharServidor();
            desktopRecepcaoThread = null;
        }

        if (desktopEnvioThread != null) {

            desktopEnvioThread.fecharServidor();
            desktopEnvioThread = null;
        }

        if (desktopJsonRecepcaoThread != null) {

            desktopJsonRecepcaoThread.fecharServidor();
            desktopJsonRecepcaoThread = null;
        }
    }

    // =========================================================
    // 10. SERVIDOR WEB: SOMENTE HTTPS
    // =========================================================

    public static synchronized void iniciarServidorWeb()
            throws IOException {

        if (servidorWebTLS != null) {
            return;
        }

        inicializarRepositorio();

        Files.createDirectories(DiretorioFigurinhas);

        SSLContext contexto;

        try {

            contexto = ConfiguracaoTLS.criarContextoServidor();

        } catch (GeneralSecurityException erro) {

            throw new IOException(
                    "Nao foi possivel carregar a configuracao TLS.",
                    erro
            );
        }

        HttpsServer servidor = HttpsServer.create(
                new InetSocketAddress("0.0.0.0", PortaWebTLS),
                0
        );

        servidor.setHttpsConfigurator(
                new HttpsConfigurator(contexto) {

            @Override
            public void configure(HttpsParameters parametros) {

                SSLContext sslContexto = getSSLContext();

                SSLParameters parametrosSSL =
                        sslContexto.getDefaultSSLParameters();

                parametrosSSL.setProtocols(
                        new String[]{
                            "TLSv1.3",
                            "TLSv1.2"
                        }
                );

                parametros.setSSLParameters(parametrosSSL);
            }
        });

        ExecutorService executor =
                Executors.newVirtualThreadPerTaskExecutor();

        configurarRotasWeb(servidor);

        servidor.setExecutor(executor);

        try {

            servidor.start();

            servidorWebTLS = servidor;
            executorWebTLS = executor;

            System.out.println(
                    "Servico Web HTTPS ativo na porta "
                    + PortaWebTLS
            );

        } catch (RuntimeException erro) {

            servidor.stop(0);
            executor.shutdownNow();

            throw new IOException(
                    "Nao foi possivel iniciar o servidor HTTPS.",
                    erro
            );
        }
    }

    /**
     * Todos os endpoints sao registrados exclusivamente
     * no servidor HTTPS.
     */
    private static void configurarRotasWeb(HttpServer servidor) {

        servidor.createContext(
                "/api/messages",
                Util::atenderMensagensWeb
        );

        servidor.createContext(
                "/api/stickers",
                Util::listarFigurinhas
        );

        servidor.createContext(
                "/api/avatars",
                Util::listarAvatares
        );

        servidor.createContext(
                "/api/emojis",
                Util::listarEmojis
        );

        servidor.createContext(
                "/figurinhas/",
                Util::servirFigurinha
        );

        servidor.createContext(
                "/api/logs",
                Util::receberLogCliente
        );

        servidor.createContext(
                "/health",
                troca -> responder(
                        troca,
                        200,
                        "OK",
                        "text/plain; charset=UTF-8"
                )
        );
    }

    public static synchronized void pararServidorWeb() {

        if (servidorWebTLS != null) {

            servidorWebTLS.stop(0);
            servidorWebTLS = null;
        }

        if (executorWebTLS != null) {

            executorWebTLS.shutdownNow();
            executorWebTLS = null;
        }

        // O coletor nao pode permanecer ativo sem o HTTPS.
        coletorLogsAtivo = false;

        System.out.println(
                "Servico Web HTTPS encerrado."
        );
    }

    // =========================================================
    // 11. COLETOR DE LOGS INTEGRADO AO HTTPS
    // =========================================================

    /**
     * Ativa o endpoint /api/logs no servidor HTTPS existente.
     * Nao cria outro servidor nem abre outra porta.
     */
    public static synchronized void iniciarColetorLogs()
            throws IOException {

        if (servidorWebTLS == null) {

            throw new IOException(
                    "Ative o servidor Web HTTPS antes do coletor de logs."
            );
        }

        coletorLogsAtivo = true;

        System.out.println(
                "Coletor de logs HTTPS ativo na porta "
                + PortaWebTLS
        );
    }

    /**
     * Desativa somente o recebimento de logs.
     * O servidor HTTPS continua funcionando.
     */
    public static synchronized void pararColetorLogs() {

        coletorLogsAtivo = false;

        System.out.println(
                "Coletor de logs HTTPS desativado."
        );
    }

    // =========================================================
    // 12. API DE MENSAGENS JSON
    // =========================================================

    private static void atenderMensagensWeb(
            HttpExchange troca) throws IOException {

        adicionarCabecalhosCors(troca);

        String metodo = troca.getRequestMethod();

        if ("OPTIONS".equalsIgnoreCase(metodo)) {

            troca.sendResponseHeaders(204, -1);
            troca.close();

            return;
        }

        if (!"POST".equalsIgnoreCase(metodo)) {

            responder(
                    troca,
                    405,
                    "Metodo nao permitido",
                    "text/plain; charset=UTF-8"
            );

            return;
        }

        String corpo = new String(
                troca.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8
        );

        ChatMessage requisicao;

        try {

            if (corpo.isBlank()) {

                throw new IllegalArgumentException(
                        "Corpo da requisicao vazio."
                );
            }

            requisicao = JsonProtocol.deserializeLine(corpo);

        } catch (Exception erro) {

            responder(
                    troca,
                    400,
                    JsonProtocol.serializeLine(
                            criarErroWeb(
                                    null,
                                    "JSON_INVALIDO",
                                    "Nao foi possivel interpretar o JSON."
                            )
                    ),
                    "application/json; charset=UTF-8"
            );

            return;
        }

        try {

            // ---------------------------------------------
            // CONSULTA DO HISTORICO
            // ---------------------------------------------

            if (requisicao.type()
                    == ChatMessage.Type.HISTORY_REQUEST) {

                if (requisicao.sender().isBlank()) {

                    responder(
                            troca,
                            400,
                            JsonProtocol.serializeLine(
                                    criarErroWeb(
                                            requisicao.id(),
                                            "REMETENTE_INVALIDO",
                                            "O remetente deve ser informado."
                                    )
                            ),
                            "application/json; charset=UTF-8"
                    );

                    return;
                }

                ChatMessage resposta = new ChatMessage(

                        ChatMessage.CURRENT_VERSION,

                        null,

                        requisicao.id(),

                        ChatMessage.Type.HISTORY_RESPONSE,

                        "middleware",

                        requisicao.sender(),

                        obterHistoricoHtml(),

                        "", "", "", "",

                        Instant.now().toString(),

                        Map.of(
                                "status", "SUCCESS",
                                "format", "text/html"
                        )
                );

                responder(
                        troca,
                        200,
                        JsonProtocol.serializeLine(resposta),
                        "application/json; charset=UTF-8"
                );

                System.out.println(
                        "Historico JSON HTTPS enviado para "
                        + requisicao.sender()
                        + " | IP: " + ipRemoto(troca)
                );

                return;
            }

            // ---------------------------------------------
            // ENVIO DE MENSAGEM
            // ---------------------------------------------

            if (requisicao.type()
                    == ChatMessage.Type.SEND_MESSAGE) {

                if (requisicao.sender().isBlank()
                        || requisicao.sender().length() > 20) {

                    responder(
                            troca,
                            400,
                            JsonProtocol.serializeLine(
                                    criarErroWeb(
                                            requisicao.id(),
                                            "REMETENTE_INVALIDO",
                                            "Remetente invalido."
                                    )
                            ),
                            "application/json; charset=UTF-8"
                    );

                    return;
                }

                if (requisicao.content().isBlank()
                        && requisicao.sticker().isBlank()) {

                    responder(
                            troca,
                            400,
                            JsonProtocol.serializeLine(
                                    criarErroWeb(
                                            requisicao.id(),
                                            "CONTEUDO_VAZIO",
                                            "A mensagem nao pode estar vazia."
                                    )
                            ),
                            "application/json; charset=UTF-8"
                    );

                    return;
                }

                String mensagemHtml =
                        RenderizadorMensagemHtml.renderizar(requisicao);

                adicionarMensagem(mensagemHtml);

                ChatMessage confirmacao = new ChatMessage(

                        ChatMessage.CURRENT_VERSION,

                        null,

                        requisicao.id(),

                        ChatMessage.Type.ACK,

                        "middleware",

                        requisicao.sender(),

                        "Mensagem recebida e registrada.",

                        "", "", "", "",

                        Instant.now().toString(),

                        Map.of(
                                "status", "SUCCESS"
                        )
                );

                responder(
                        troca,
                        200,
                        JsonProtocol.serializeLine(confirmacao),
                        "application/json; charset=UTF-8"
                );

                System.out.println(
                        "Mensagem JSON HTTPS recebida de "
                        + requisicao.sender()
                        + " | IP: " + ipRemoto(troca)
                );

                return;
            }

            // ---------------------------------------------
            // OPERACAO NAO SUPORTADA
            // ---------------------------------------------

            responder(
                    troca,
                    400,
                    JsonProtocol.serializeLine(
                            criarErroWeb(
                                    requisicao.id(),
                                    "OPERACAO_NAO_SUPORTADA",
                                    "Operacao nao suportada pelo middleware."
                            )
                    ),
                    "application/json; charset=UTF-8"
            );

        } catch (Exception erro) {

            registrarErroServidor(
                    "web",
                    "INTERNO/HTTPS",
                    ipRemoto(troca),
                    "Util.atenderMensagensWeb",
                    erro
            );

            responder(
                    troca,
                    500,
                    JsonProtocol.serializeLine(
                            criarErroWeb(
                                    requisicao.id(),
                                    "ERRO_INTERNO",
                                    "Nao foi possivel processar a requisicao."
                            )
                    ),
                    "application/json; charset=UTF-8"
            );
        }
    }

    private static ChatMessage criarErroWeb(
            String correlationId,
            String codigo,
            String descricao) {

        return new ChatMessage(

                ChatMessage.CURRENT_VERSION,

                null,

                correlationId,

                ChatMessage.Type.ERROR,

                "middleware",

                "",

                descricao,

                "", "", "", "",

                Instant.now().toString(),

                Map.of(
                        "code", codigo
                )
        );
    }

    // =========================================================
    // 13. LISTAGEM DE FIGURINHAS E AVATARES
    // =========================================================

    private static void listarFigurinhas(
            HttpExchange troca) throws IOException {

        if (!"GET".equalsIgnoreCase(troca.getRequestMethod())) {

            responder(
                    troca,
                    405,
                    "Metodo nao permitido",
                    "text/plain; charset=UTF-8"
            );

            return;
        }

        listarFigurinhasJson(
                troca,
                DiretorioFigurinhas
        );
    }

    private static void listarAvatares(
            HttpExchange troca) throws IOException {

        if (!"GET".equalsIgnoreCase(troca.getRequestMethod())) {

            responder(
                    troca,
                    405,
                    "Metodo nao permitido",
                    "text/plain; charset=UTF-8"
            );

            return;
        }

        Path pastaAvatares =
                DiretorioFigurinhas.resolve("avatars");

        Files.createDirectories(pastaAvatares);

        // Mantem o formato texto utilizado pelo cliente Web existente.
        listarImagensDoDiretorio(
                troca,
                pastaAvatares
        );
    }

    /**
     * Retorna o catalogo de figurinhas como um array JSON de nomes.
     * Exemplo: ["figurinha1.png", "figurinha2.gif"]
     */
    private static void listarFigurinhasJson(
            HttpExchange troca,
            Path diretorio) throws IOException {

        try (var arquivos = Files.list(diretorio)) {

            List<String> nomes = arquivos
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(Util::ehArquivoImagem)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();

            String resultado = serializarListaJson(nomes);

            responder(
                    troca,
                    200,
                    resultado,
                    "application/json; charset=UTF-8"
            );

        } catch (IOException erro) {

            registrarErroServidor(
                    "web",
                    "INTERNO/HTTPS",
                    ipRemoto(troca),
                    "Util.listarFigurinhasJson",
                    erro
            );

            responder(
                    troca,
                    500,
                    "{\"error\":\"Erro ao listar figurinhas\"}",
                    "application/json; charset=UTF-8"
            );
        }
    }

    /**
     * Serializa uma lista de nomes como array JSON sem depender
     * de uma biblioteca externa para uma estrutura tao simples.
     */
    private static String serializarListaJson(List<String> valores) {

        StringBuilder json = new StringBuilder("[");

        for (int i = 0; i < valores.size(); i++) {

            if (i > 0) {
                json.append(',');
            }

            json.append('"')
                    .append(escaparStringJson(valores.get(i)))
                    .append('"');
        }

        return json.append(']').toString();
    }

    /**
     * Escapa os caracteres especiais exigidos pelo formato JSON.
     */
    private static String escaparStringJson(String valor) {

        StringBuilder resultado = new StringBuilder();

        for (int i = 0; i < valor.length(); i++) {

            char caractere = valor.charAt(i);

            switch (caractere) {
                case '"' -> resultado.append("\\\"");
                case '\\' -> resultado.append("\\\\");
                case '\b' -> resultado.append("\\b");
                case '\f' -> resultado.append("\\f");
                case '\n' -> resultado.append("\\n");
                case '\r' -> resultado.append("\\r");
                case '\t' -> resultado.append("\\t");
                default -> {
                    if (caractere < 0x20) {
                        resultado.append("\\u");
                        String hexadecimal = Integer.toHexString(caractere);
                        resultado.append("0".repeat(4 - hexadecimal.length()));
                        resultado.append(hexadecimal);
                    } else {
                        resultado.append(caractere);
                    }
                }
            }
        }

        return resultado.toString();
    }

    /**
     * Mantem a listagem textual para os endpoints que ainda utilizam
     * uma linha por arquivo, como /api/avatars.
     */
    private static void listarImagensDoDiretorio(
            HttpExchange troca,
            Path diretorio) throws IOException {

        try (var arquivos = Files.list(diretorio)) {

            String resultado = arquivos
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(Util::ehArquivoImagem)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(
                            java.util.stream.Collectors.joining("\n")
                    );

            responder(
                    troca,
                    200,
                    resultado,
                    "text/plain; charset=UTF-8"
            );

        } catch (IOException erro) {

            registrarErroServidor(
                    "web",
                    "INTERNO/HTTPS",
                    ipRemoto(troca),
                    "Util.listarImagensDoDiretorio",
                    erro
            );

            responder(
                    troca,
                    500,
                    "Erro ao listar imagens",
                    "text/plain; charset=UTF-8"
            );
        }
    }

    private static boolean ehArquivoImagem(String nome) {

        String extensao = nome.toLowerCase(Locale.ROOT);

        return extensao.endsWith(".png")
                || extensao.endsWith(".gif")
                || extensao.endsWith(".jpg")
                || extensao.endsWith(".jpeg")
                || extensao.endsWith(".webp");
    }

    // =========================================================
    // 14. EMOJIS
    // =========================================================

    private static void listarEmojis(
            HttpExchange troca) throws IOException {

        if (!"GET".equalsIgnoreCase(troca.getRequestMethod())) {

            responder(
                    troca,
                    405,
                    "Metodo nao permitido",
                    "text/plain; charset=UTF-8"
            );

            return;
        }

        String resultado = String.join(
                "\n",
                EMOJIS_COMPARTILHADOS
        );

        responder(
                troca,
                200,
                resultado,
                "text/plain; charset=UTF-8"
        );
    }

    // =========================================================
    // 15. SERVIR FIGURINHAS E AVATARES
    // =========================================================

    private static void servirFigurinha(
            HttpExchange troca) throws IOException {

        if (!"GET".equalsIgnoreCase(troca.getRequestMethod())) {

            responder(
                    troca,
                    405,
                    "Metodo nao permitido",
                    "text/plain; charset=UTF-8"
            );

            return;
        }

        try {

            String caminho = troca.getRequestURI()
                    .getRawPath()
                    .substring("/figurinhas/".length());

            caminho = java.net.URLDecoder.decode(
                    caminho,
                    StandardCharsets.UTF_8
            );

            Path raiz = DiretorioFigurinhas.toRealPath();

            Path arquivo = raiz.resolve(caminho).normalize();

            // Impede acesso a arquivos fora da pasta central.
            if (!arquivo.startsWith(raiz)
                    || !Files.isRegularFile(arquivo)) {

                responder(
                        troca,
                        404,
                        "Imagem nao encontrada",
                        "text/plain; charset=UTF-8"
                );

                return;
            }

            // Tambem impede escapes por links simbolicos.
            arquivo = arquivo.toRealPath();

            if (!arquivo.startsWith(raiz)) {

                responder(
                        troca,
                        404,
                        "Imagem nao encontrada",
                        "text/plain; charset=UTF-8"
                );

                return;
            }

            String nome = arquivo.getFileName()
                    .toString()
                    .toLowerCase(Locale.ROOT);

            String tipo = switch (
                    nome.substring(nome.lastIndexOf('.') + 1)
            ) {

                case "png" -> "image/png";
                case "gif" -> "image/gif";
                case "jpg", "jpeg" -> "image/jpeg";
                case "webp" -> "image/webp";

                default -> "application/octet-stream";
            };

            byte[] imagem = Files.readAllBytes(arquivo);

            troca.getResponseHeaders().set(
                    "Content-Type",
                    tipo
            );

            troca.getResponseHeaders().set(
                    "Cache-Control",
                    "public, max-age=3600"
            );

            troca.getResponseHeaders().set(
                    "X-Content-Type-Options",
                    "nosniff"
            );

            troca.getResponseHeaders().set(
                    "Access-Control-Allow-Origin",
                    "*"
            );

            troca.sendResponseHeaders(200, imagem.length);

            try (var saida = troca.getResponseBody()) {
                saida.write(imagem);
            }

        } catch (java.nio.file.NoSuchFileException erro) {

            responder(
                    troca,
                    404,
                    "Imagem nao encontrada",
                    "text/plain; charset=UTF-8"
            );

        } catch (Exception erro) {

            registrarErroServidor(
                    "web",
                    "INTERNO/HTTPS",
                    ipRemoto(troca),
                    "Util.servirFigurinha",
                    erro
            );

            responder(
                    troca,
                    500,
                    "Erro ao carregar imagem",
                    "text/plain; charset=UTF-8"
            );
        }
    }

    // =========================================================
    // 16. COLETOR DE LOGS HTTPS
    // =========================================================

    /**
     * Formato do POST:
     * tipo TAB computador TAB Classe:Método TAB mensagem
     *
     * O IP e a data sao identificados pelo servidor.
     */
    private static void receberLogCliente(
            HttpExchange troca) throws IOException {

        adicionarCabecalhosCors(troca);

        if ("OPTIONS".equalsIgnoreCase(troca.getRequestMethod())) {

            troca.sendResponseHeaders(204, -1);
            troca.close();

            return;
        }

        if (!"POST".equalsIgnoreCase(troca.getRequestMethod())) {

            responder(
                    troca,
                    405,
                    "Metodo nao permitido",
                    "text/plain; charset=UTF-8"
            );

            return;
        }

        if (!coletorLogsAtivo) {

            responder(
                    troca,
                    503,
                    "Coletor de logs desativado.",
                    "text/plain; charset=UTF-8"
            );

            return;
        }

        try {

            String corpo = new String(
                    troca.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            String[] campos = corpo.split("\\t", 4);

            if (campos.length < 4) {

                responder(
                        troca,
                        400,
                        "Formato de log invalido",
                        "text/plain; charset=UTF-8"
                );

                return;
            }

            registrarErroCliente(
                    campos[0],
                    campos[1],
                    ipRemoto(troca),
                    campos[2],
                    campos[3]
            );

            responder(
                    troca,
                    200,
                    "OK",
                    "text/plain; charset=UTF-8"
            );

        } catch (Exception erro) {

            registrarErroServidor(
                    "internal",
                    "INTERNO/HTTPS",
                    ipRemoto(troca),
                    "Util.receberLogCliente",
                    erro
            );

            responder(
                    troca,
                    500,
                    "Erro ao registrar log",
                    "text/plain; charset=UTF-8"
            );
        }
    }

    // =========================================================
    // 17. REGISTRO DE ERROS
    // =========================================================

    /**
     * Grava erros enviados pelos clientes.
     *
     * Exemplo:
     * client_desktop_errors.log
     * client_web_errors.log
     */
    public static void registrarErroCliente(
            String tipo,
            String computador,
            String ip,
            String classeMetodo,
            String mensagem) {

        String cliente = normalizarTipo(tipo);

        Path arquivo = DiretorioLogs.resolve(
                "client_" + cliente + "_errors.log"
        );

        String registro = dataHora()
                + " | " + limpar(computador)
                + " | " + limpar(ip)
                + " | " + limpar(classeMetodo)
                + " | " + limpar(mensagem)
                + System.lineSeparator()
                + System.lineSeparator();

        escreverLogSeguro(arquivo, registro);
    }

    /**
     * Grava erros internos do middleware.
     */
    public static void registrarErroServidor(
            String servico,
            String cliente,
            String ip,
            String classeMetodo,
            Throwable erro) {

        String nomeServico = normalizarServico(servico);

        Path arquivo = DiretorioLogs.resolve(
                "server_middleware_" + nomeServico + "_errors.log"
        );

        String mensagem = erro == null
                ? "Erro sem detalhes"
                : erro.getClass().getSimpleName()
                        + ": " + erro.getMessage();

        String identificacao = (cliente == null || cliente.isBlank())
                ? "INTERNO/NÃO APLICÁVEL"
                : cliente;

        String registro = dataHora()
                + " | " + nomeServico
                + " | " + limpar(identificacao)
                + " | " + limpar(ip)
                + " | " + limpar(classeMetodo)
                + " | " + limpar(mensagem)
                + System.lineSeparator()
                + System.lineSeparator();

        escreverLogSeguro(arquivo, registro);
    }

    /**
     * Escrita sincronizada dos logs.
     */
    private static void escreverLogSeguro(
            Path arquivo,
            String registro) {

        Object trava = travasArquivos.computeIfAbsent(
                arquivo.toAbsolutePath().normalize(),
                chave -> new Object()
        );

        synchronized (trava) {

            try {

                Files.createDirectories(DiretorioLogs);

                Files.writeString(
                        arquivo,
                        registro,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND
                );

            } catch (IOException erro) {

                System.err.println(
                        "Falha ao gravar log "
                        + arquivo + ": " + erro.getMessage()
                );

                erro.printStackTrace(System.err);
            }
        }
    }

    // =========================================================
    // 18. METODOS AUXILIARES
    // =========================================================

    private static void responder(
            HttpExchange troca,
            int status,
            String corpo,
            String contentType) throws IOException {

        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);

        troca.getResponseHeaders().set(
                "Content-Type",
                contentType
        );

        adicionarCabecalhosCors(troca);

        troca.sendResponseHeaders(status, bytes.length);

        try (var saida = troca.getResponseBody()) {
            saida.write(bytes);
        }
    }

    private static void adicionarCabecalhosCors(
            HttpExchange troca) {

        troca.getResponseHeaders().set(
                "Access-Control-Allow-Origin",
                "*"
        );

        troca.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET, POST, OPTIONS"
        );

        troca.getResponseHeaders().set(
                "Access-Control-Allow-Headers",
                "Content-Type"
        );

        troca.getResponseHeaders().set(
                "Strict-Transport-Security",
                "max-age=31536000"
        );
    }

    private static String ipRemoto(HttpExchange troca) {

        if (troca.getRemoteAddress() == null
                || troca.getRemoteAddress().getAddress() == null) {

            return "NÃO APLICÁVEL";
        }

        return troca.getRemoteAddress()
                .getAddress()
                .getHostAddress();
    }

    private static String dataHora() {

        return LocalDateTime.now().format(FORMATO_DATA);
    }

    private static String limpar(String valor) {

        if (valor == null || valor.isBlank()) {
            return "NÃO APLICÁVEL";
        }

        return valor
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replace('|', '/')
                .trim();
    }

    private static String normalizarTipo(String tipo) {

        String valor = tipo == null
                ? "desconhecido"
                : tipo.toLowerCase(Locale.ROOT).trim();

        valor = valor.replaceAll("[^a-z0-9_-]", "_");

        return valor.isBlank()
                ? "desconhecido"
                : valor;
    }

    private static String normalizarServico(String servico) {

        String valor = normalizarTipo(servico);

        return switch (valor) {

            case "desktop", "web", "internal" -> valor;

            default -> "internal";
        };
    }

    // =========================================================
    // 19. ENCERRAMENTO GERAL
    // =========================================================

    public static synchronized void pararTodosServicos() {

        pararColetorLogs();
        pararServidorWeb();
        pararDesktop();
    }

    // =========================================================
    // 20. EMOJIS COMPARTILHADOS
    // =========================================================

    private static final String[] EMOJIS_COMPARTILHADOS = {
        "😀", "😁", "😂", "🤣", "😅", "😊",
        "😍", "😎", "😘", "😜", "😢", "😭",
        "😡", "😱", "🤔", "😴", "🥳", "🤩",
        "👍", "👎", "👏", "🙏", "❤️", "💔",
        "🔥", "🎉", "💯", "🤝", "👀", "💀"
    };
}
