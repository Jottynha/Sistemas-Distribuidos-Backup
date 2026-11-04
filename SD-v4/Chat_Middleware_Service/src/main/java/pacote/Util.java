package pacote;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class Util {
    // PORTAS
    public static final int PortaEnvioDesktop = 6661;
    public static final int PortaRecepcaoDesktop = 6662;
    public static final int PortaWeb = 8081;
    public static final int PortaLogs = 8082;
    // DIRETORIOS: sempre relativos a pasta do modulo/JAR, nao ao terminal.
    private static final Path DiretorioBase = localizarDiretorioBase();
    public static final Path DiretorioDados = DiretorioBase.resolve("data");
    public static final Path DiretorioLogs = DiretorioDados.resolve("logs");
    public static final Path DiretorioFigurinhas = DiretorioBase.resolve("figurinhas");
    public static final String PathRepDesktop =
            DiretorioDados.resolve("RepositorioDesktop.txt").toString();
    // THREADS DESKTOP EXISTENTES
    public static DesktopRecepcaoThread desktopRecepcaoThread;
    public static DesktopEnvioThread desktopEnvioThread;
    // HISTORICO COMPARTILHADO
    private static final List<String> historicoMensagens = new ArrayList<>();
    private static final ReentrantReadWriteLock travaHistorico =
            new ReentrantReadWriteLock(true);
    private static boolean historicoInicializado = false;
    // SERVIDORES WEB E LOGS
    private static HttpServer servidorWeb;
    private static HttpServer coletorLogs;
    private static ExecutorService executorWeb;
    private static ExecutorService executorLogs;
    // SINCRONIZACAO DOS ARQUIVOS DE LOG
    private static final ConcurrentMap<Path, Object> travasArquivos =
            new ConcurrentHashMap<>();
    private static final DateTimeFormatter FORMATO_DATA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private Util() {
    }

    /**
     * Descobre a pasta do modulo a partir da classe compilada ou do JAR.
     * Pode ser sobrescrita por -Dchat.server.baseDir=... ou CHAT_SERVER_BASE_DIR.
     */
    private static Path localizarDiretorioBase() {

        String configurado = System.getProperty("chat.server.baseDir");
        if (configurado == null || configurado.isBlank()) {
            configurado = System.getenv("CHAT_SERVER_BASE_DIR");
        }
        if (configurado != null && !configurado.isBlank()) {
            return Path.of(configurado).toAbsolutePath().normalize();
        }

        try {
            Path local = Path.of(
                    Util.class.getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            ).toAbsolutePath().normalize();

            // NetBeans/Maven: .../Chat_Middleware_Service/target/classes
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

            // JAR: .../Chat_Middleware_Service/target/aplicacao.jar
            Path parent = local.getParent();
            if (parent != null
                    && parent.getFileName() != null
                    && parent.getFileName().toString().equals("target")
                    && parent.getParent() != null) {
                return parent.getParent();
            }
            return parent != null ? parent : Path.of(System.getProperty("user.dir"));

        } catch (Exception erro) {
            System.err.println("Nao foi possivel localizar a pasta do middleware: "
                    + erro.getMessage());
            return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        }
    }

    // =========================================================
    // 1. HISTORICO EM RAM E TXT
    // =========================================================
    public static synchronized void inicializarRepositorio()
            throws IOException {

        if (historicoInicializado) {
            return;
        }

        Files.createDirectories(DiretorioDados);
        Files.createDirectories(DiretorioLogs);
        Files.createDirectories(DiretorioFigurinhas);
        Files.createDirectories(DiretorioFigurinhas.resolve("avatars"));

        System.out.println("========== DIRETORIOS DO MIDDLEWARE ==========");
        System.out.println("Base: " + DiretorioBase);
        System.out.println("Dados: " + DiretorioDados);
        System.out.println("Logs: " + DiretorioLogs);
        System.out.println("Figurinhas: " + DiretorioFigurinhas);
        System.out.println("Historico: " + PathRepDesktop);
        System.out.println("==============================================");

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

                        ByteBuffer ultimoByte = ByteBuffer.allocate(1);

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
    // 2. CONTROLE DO SERVICO DESKTOP
    // =========================================================

    public static synchronized void iniciarDesktop()
            throws IOException {

        inicializarRepositorio();

        if (desktopRecepcaoThread != null
                || desktopEnvioThread != null) {
            return;
        }

        desktopRecepcaoThread = new DesktopRecepcaoThread();
        desktopEnvioThread = new DesktopEnvioThread();

        Thread.ofVirtual()
                .name("desktop-recepcao")
                .start(desktopRecepcaoThread);

        Thread.ofVirtual()
                .name("desktop-envio")
                .start(desktopEnvioThread);
    }

    public static synchronized void pararDesktop() {

        if (desktopRecepcaoThread != null) {
            desktopRecepcaoThread.fecharServidor();
            desktopRecepcaoThread = null;
        }

        if (desktopEnvioThread != null) {
            desktopEnvioThread.fecharServidor();
            desktopEnvioThread = null;
        }
    }

    // =========================================================
    // 3. SERVIDOR WEB HTTP
    // =========================================================

    public static synchronized void iniciarServidorWeb()
            throws IOException {

        if (servidorWeb != null) {
            return;
        }

        inicializarRepositorio();

        Files.createDirectories(DiretorioFigurinhas);

        HttpServer servidor = HttpServer.create(
                new InetSocketAddress("0.0.0.0", PortaWeb),
                0
        );

        ExecutorService executor =
                Executors.newVirtualThreadPerTaskExecutor();

        servidor.setExecutor(executor);

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
                "/health",
                troca -> responder(
                        troca,
                        200,
                        "OK",
                        "text/plain; charset=UTF-8"
                )
        );

        servidor.start();

        servidorWeb = servidor;
        executorWeb = executor;

        System.out.println(
                "Servico Web ativo na porta " + PortaWeb
        );
    }

    public static synchronized void pararServidorWeb() {

        if (servidorWeb != null) {
            servidorWeb.stop(0);
            servidorWeb = null;
        }

        if (executorWeb != null) {
            executorWeb.shutdownNow();
            executorWeb = null;
        }
    }

    private static void atenderMensagensWeb(
            HttpExchange troca) throws IOException {

        adicionarCabecalhosCors(troca);

        String metodo = troca.getRequestMethod();

        if ("OPTIONS".equalsIgnoreCase(metodo)) {
            troca.sendResponseHeaders(204, -1);
            troca.close();
            return;
        }

        try {

            if ("GET".equalsIgnoreCase(metodo)) {

                responder(
                        troca,
                        200,
                        obterHistoricoHtml(),
                        "text/html; charset=UTF-8"
                );

            } else if ("POST".equalsIgnoreCase(metodo)) {

                String mensagem = new String(
                        troca.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8
                );

                adicionarMensagem(mensagem);

                responder(
                        troca,
                        200,
                        "OK",
                        "text/plain; charset=UTF-8"
                );

            } else {

                responder(
                        troca,
                        405,
                        "Metodo nao permitido",
                        "text/plain; charset=UTF-8"
                );
            }

        } catch (Exception erro) {

            registrarErroServidor(
                    "web",
                    "INTERNO/NÃO APLICÁVEL",
                    ipRemoto(troca),
                    "Util.atenderMensagensWeb",
                    erro
            );

            responder(
                    troca,
                    500,
                    "ERRO: nao foi possivel processar a requisicao",
                    "text/plain; charset=UTF-8"
            );
        }
    }

    /**
     * Lista as figurinhas que estao na pasta central.
     */

    private static void listarFigurinhas(
            HttpExchange troca) throws IOException {

        if (!"GET".equalsIgnoreCase(troca.getRequestMethod())) {
            responder(
                    troca, 405, "Metodo nao permitido",
                    "text/plain; charset=UTF-8"
            );
            return;
        }

        listarImagensDoDiretorio(
                troca,
                DiretorioFigurinhas
        );
    }


    private static void listarAvatares(
            HttpExchange troca) throws IOException {

        if (!"GET".equalsIgnoreCase(troca.getRequestMethod())) {
            responder(
                    troca, 405, "Metodo nao permitido",
                    "text/plain; charset=UTF-8"
            );
            return;
        }

        Path pastaAvatares =
                DiretorioFigurinhas.resolve("avatars");

        Files.createDirectories(pastaAvatares);

        listarImagensDoDiretorio(
                troca,
                pastaAvatares
        );
    }


    private static void listarImagensDoDiretorio(
            HttpExchange troca,
            Path diretorio) throws IOException {

        try (var arquivos = Files.list(diretorio)) {

            String resultado = arquivos
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(Util::ehArquivoImagem)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(java.util.stream.Collectors.joining("\n"));

            responder(
                    troca,
                    200,
                    resultado,
                    "text/plain; charset=UTF-8"
            );

        } catch (IOException erro) {

            registrarErroServidor(
                    "web",
                    "INTERNO/NÃO APLICÁVEL",
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

        String extensao = nome.toLowerCase(
                java.util.Locale.ROOT
        );

        return extensao.endsWith(".png")
                || extensao.endsWith(".gif")
                || extensao.endsWith(".jpg")
                || extensao.endsWith(".jpeg")
                || extensao.endsWith(".webp");
    }


    private static void listarEmojis(
            HttpExchange troca) throws IOException {

        if (!"GET".equalsIgnoreCase(troca.getRequestMethod())) {
            responder(
                    troca, 405, "Metodo nao permitido",
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






    private static void servirFigurinha(
            HttpExchange troca) throws IOException {

        if (!"GET".equalsIgnoreCase(troca.getRequestMethod())) {
            responder(
                    troca, 405, "Metodo nao permitido",
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

            Path raiz = DiretorioFigurinhas
                    .toRealPath();

            Path arquivo = raiz.resolve(caminho)
                    .normalize();

            // Impede acesso a arquivos fora da pasta central.
            if (!arquivo.startsWith(raiz)
                    || !Files.isRegularFile(arquivo)) {

                responder(
                        troca, 404, "Imagem nao encontrada",
                        "text/plain; charset=UTF-8"
                );
                return;
            }

            // Tambem impede escapes por links simbolicos.
            arquivo = arquivo.toRealPath();

            if (!arquivo.startsWith(raiz)) {
                responder(
                        troca, 404, "Imagem nao encontrada",
                        "text/plain; charset=UTF-8"
                );
                return;
            }

            String nome = arquivo.getFileName()
                    .toString()
                    .toLowerCase(java.util.Locale.ROOT);

            String tipo = switch (nome.substring(
                    nome.lastIndexOf('.') + 1)) {

                case "png" -> "image/png";
                case "gif" -> "image/gif";
                case "jpg", "jpeg" -> "image/jpeg";
                case "webp" -> "image/webp";

                default -> "application/octet-stream";
            };

            byte[] imagem = Files.readAllBytes(arquivo);

            troca.getResponseHeaders().set(
                    "Content-Type", tipo
            );

            troca.getResponseHeaders().set(
                    "Cache-Control", "public, max-age=3600"
            );

            troca.getResponseHeaders().set(
                    "X-Content-Type-Options", "nosniff"
            );

            troca.getResponseHeaders().set(
                    "Access-Control-Allow-Origin", "*"
            );

            troca.sendResponseHeaders(200, imagem.length);

            try (var saida = troca.getResponseBody()) {
                saida.write(imagem);
            }

        } catch (java.nio.file.NoSuchFileException erro) {

            responder(
                    troca, 404, "Imagem nao encontrada",
                    "text/plain; charset=UTF-8"
            );

        } catch (Exception erro) {

            registrarErroServidor(
                    "web",
                    "INTERNO/NÃO APLICÁVEL",
                    ipRemoto(troca),
                    "Util.servirFigurinha",
                    erro
            );

            responder(
                    troca, 500, "Erro ao carregar imagem",
                    "text/plain; charset=UTF-8"
            );
        }
    }


    // =========================================================
    // 4. COLETOR UNICO DE LOGS DOS CLIENTES
    // =========================================================

    public static synchronized void iniciarColetorLogs()
            throws IOException {

        if (coletorLogs != null) {
            return;
        }

        Files.createDirectories(DiretorioLogs);

        HttpServer servidor = HttpServer.create(
                new InetSocketAddress("0.0.0.0", PortaLogs),
                0
        );

        ExecutorService executor =
                Executors.newVirtualThreadPerTaskExecutor();

        servidor.setExecutor(executor);

        servidor.createContext(
                "/api/logs",
                Util::receberLogCliente
        );

        servidor.start();

        coletorLogs = servidor;
        executorLogs = executor;

        System.out.println(
                "Coletor de logs ativo na porta " + PortaLogs
        );
    }

    public static synchronized void pararColetorLogs() {

        if (coletorLogs != null) {
            coletorLogs.stop(0);
            coletorLogs = null;
        }

        if (executorLogs != null) {
            executorLogs.shutdownNow();
            executorLogs = null;
        }
    }

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
                    troca, 405, "Metodo nao permitido",
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
                        troca, 400, "Formato de log invalido",
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
                    troca, 200, "OK",
                    "text/plain; charset=UTF-8"
            );

        } catch (Exception erro) {

            registrarErroServidor(
                    "internal",
                    "INTERNO/NÃO APLICÁVEL",
                    ipRemoto(troca),
                    "Util.receberLogCliente",
                    erro
            );

            responder(
                    troca, 500, "Erro ao registrar log",
                    "text/plain; charset=UTF-8"
            );
        }
    }

    // =========================================================
    // 5. REGISTRO DE ERROS
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
     *
     * Exemplo:
     * server_middleware_desktop_errors.log
     * server_middleware_web_errors.log
     * server_middleware_internal_errors.log
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
    // 6. METODOS AUXILIARES
    // =========================================================

    private static void responder(
            HttpExchange troca,
            int status,
            String corpo,
            String contentType) throws IOException {

        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);

        troca.getResponseHeaders().set(
                "Content-Type", contentType
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
                "Access-Control-Allow-Origin", "*"
        );

        troca.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET, POST, OPTIONS"
        );

        troca.getResponseHeaders().set(
                "Access-Control-Allow-Headers", "Content-Type"
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
    public static synchronized void pararTodosServicos() {
        pararDesktop();
        pararServidorWeb();
        pararColetorLogs();
    } 
    private static final String[] EMOJIS_COMPARTILHADOS = {
        "😀", "😁", "😂", "🤣", "😅", "😊",
        "😍", "😎", "😘", "😜", "😢", "😭",
        "😡", "😱", "🤔", "😴", "🥳", "🤩",
        "👍", "👎", "👏", "🙏", "❤️", "💔",
        "🔥", "🎉", "💯", "🤝", "👀", "💀"
    };

}
