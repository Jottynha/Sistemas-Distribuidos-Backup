package pacote.advertising;

import protocolo.ChatMessage;
import protocolo.JsonProtocol;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.Inet4Address;
import java.util.Enumeration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/** Publica periodicamente o nome da imagem/GIF ativo por UDP multicast. */
public final class PublicidadeMulticastService implements AutoCloseable {
    private final Path diretorio;
    private final String grupoTexto;
    private final int porta;
    private final int intervaloSegundos;
    private final AtomicInteger indice = new AtomicInteger();
    private volatile boolean fechado;
    private volatile ScheduledExecutorService executor;
    private volatile MulticastSocket socket;
    private volatile String ultimaPublicidade = "";
    private final NetworkInterface interfaceRede;

    public PublicidadeMulticastService(Path diretorio) throws IOException {
        this.diretorio = diretorio.toAbsolutePath().normalize();
        this.grupoTexto = System.getenv().getOrDefault("CHAT_AD_MULTICAST_GROUP", "239.255.42.99").trim();
        this.porta = inteiro("CHAT_AD_MULTICAST_PORT", 5005);
        this.intervaloSegundos = inteiro("CHAT_AD_INTERVAL_SECONDS", 10);
        InetAddress endereco = InetAddress.getByName(grupoTexto);
        if (!endereco.isMulticastAddress()) throw new IOException("CHAT_AD_MULTICAST_GROUP nao e um endereco multicast.");
        if (porta < 1 || porta > 65535) throw new IOException("CHAT_AD_MULTICAST_PORT invalida.");
        if (intervaloSegundos < 1 || intervaloSegundos > 3600) throw new IOException("CHAT_AD_INTERVAL_SECONDS deve estar entre 1 e 3600.");
        this.interfaceRede = selecionarInterfaceMulticast(System.getenv("CHAT_AD_MULTICAST_INTERFACE"));
    }

    public synchronized void iniciar() throws IOException {
        if (isRunning()) return;
        fechado = false;
        MulticastSocket novoSocket = new MulticastSocket();
        novoSocket.setTimeToLive(1);
        novoSocket.setNetworkInterface(interfaceRede);
        socket = novoSocket;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "middleware-publicidade-multicast");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::anunciarProxima, 0, intervaloSegundos, TimeUnit.SECONDS);
        System.out.println("Publicidade multicast ativa: grupo " + grupoTexto + ":" + porta
                + " | intervalo " + intervaloSegundos + "s | interface " + interfaceRede.getName()
                + " | pasta " + diretorio);
    }

    private void anunciarProxima() {
        if (fechado) return;
        try {
            List<String> imagens;
            try (var arquivos = Files.list(diretorio)) {
                imagens = arquivos.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .filter(PublicidadeMulticastService::imagemValida)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
            }
            if (imagens.isEmpty()) {
                if (!ultimaPublicidade.equals("__EMPTY__")) {
                    System.out.println("Publicidade multicast aguardando imagens em " + diretorio);
                    ultimaPublicidade = "__EMPTY__";
                }
                return;
            }
            String nome = imagens.get(Math.floorMod(indice.getAndIncrement(), imagens.size()));
            ChatMessage aviso = new ChatMessage(
                    ChatMessage.CURRENT_VERSION, null, null,
                    ChatMessage.Type.ADVERTISEMENT_UPDATE,
                    "middleware", "todos", nome,
                    "", "", "", "", Instant.now().toString(),
                    Map.of("resource", "/publicidade/" + nome,
                           "intervalSeconds", intervaloSegundos)
            );
            byte[] dados = JsonProtocol.serializeLine(aviso).getBytes(StandardCharsets.UTF_8);
            if (dados.length > 60000) throw new IOException("Anuncio multicast ultrapassa o limite de datagrama.");
            DatagramPacket pacote = new DatagramPacket(dados, dados.length,
                    InetAddress.getByName(grupoTexto), porta);
            MulticastSocket atual = socket;
            if (atual != null && !atual.isClosed()) atual.send(pacote);
            ultimaPublicidade = nome;
            System.out.println("Publicidade anunciada por multicast: " + nome);
        } catch (Exception erro) {
            if (!fechado) System.err.println("Falha ao anunciar publicidade multicast: " + erro.getMessage());
        }
    }

    public boolean isRunning() {
        ScheduledExecutorService atual = executor;
        return !fechado && atual != null && !atual.isShutdown();
    }

    @Override
    public synchronized void close() {
        fechado = true;
        ScheduledExecutorService atual = executor;
        executor = null;
        if (atual != null) atual.shutdownNow();
        MulticastSocket atualSocket = socket;
        socket = null;
        if (atualSocket != null) atualSocket.close();
    }

    public void fechar() { close(); }


    private static NetworkInterface selecionarInterfaceMulticast(String configurada) throws IOException {
        if (configurada != null && !configurada.isBlank()) {
            String valor = configurada.trim();
            NetworkInterface iface = NetworkInterface.getByName(valor);
            if (iface == null) {
                try { iface = NetworkInterface.getByInetAddress(InetAddress.getByName(valor)); }
                catch (Exception ignored) { }
            }
            if (iface == null || !iface.isUp() || !iface.supportsMulticast()) {
                throw new IOException("CHAT_AD_MULTICAST_INTERFACE nao identifica uma interface multicast ativa: " + valor);
            }
            return iface;
        }

        NetworkInterface alternativa = null;
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (!iface.isUp() || iface.isLoopback() || !iface.supportsMulticast()) continue;
            boolean ipv4 = iface.getInterfaceAddresses().stream()
                    .anyMatch(endereco -> endereco.getAddress() instanceof Inet4Address);
            if (!ipv4) continue;
            String nomes = (iface.getName() + " " + iface.getDisplayName()).toLowerCase(java.util.Locale.ROOT);
            if (nomes.contains("wi-fi") || nomes.contains("wifi") || nomes.contains("wlan") || nomes.contains("wireless")) {
                return iface;
            }
            if (alternativa == null) alternativa = iface;
        }
        if (alternativa == null) throw new IOException("Nenhuma interface IPv4 com suporte a multicast foi encontrada.");
        return alternativa;
    }

    private static boolean imagemValida(String nome) {
        String n = nome.toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".gif") || n.endsWith(".jpg")
                || n.endsWith(".jpeg") || n.endsWith(".webp");
    }

    private static int inteiro(String nome, int padrao) throws IOException {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) return padrao;
        try { return Integer.parseInt(valor.trim()); }
        catch (NumberFormatException erro) { throw new IOException(nome + " deve ser inteiro.", erro); }
    }
}
