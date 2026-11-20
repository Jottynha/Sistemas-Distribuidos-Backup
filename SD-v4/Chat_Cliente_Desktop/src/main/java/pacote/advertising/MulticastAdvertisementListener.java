package pacote.advertising;

import protocolo.ChatMessage;
import protocolo.JsonProtocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.function.Consumer;

/** Recebe avisos ADVERTISEMENT_UPDATE por UDP multicast, com reconexao automatica. */
public final class MulticastAdvertisementListener implements AutoCloseable {
    private final String groupText;
    private final int port;
    private final Consumer<String> callback;
    private volatile boolean running;
    private volatile MulticastSocket socket;
    private volatile NetworkInterface networkInterface;

    public MulticastAdvertisementListener(Consumer<String> callback) {
        this.groupText = System.getenv().getOrDefault("CHAT_AD_MULTICAST_GROUP", "239.255.42.99").trim();
        this.port = parsePort(System.getenv("CHAT_AD_MULTICAST_PORT"), 5005);
        this.callback = callback;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        Thread.ofVirtual().name("desktop-publicidade-multicast").start(this::receber);
    }

    private void receber() {
        while (running) {
            MulticastSocket atual = null;
            InetAddress grupo = null;
            NetworkInterface iface = null;
            try {
                grupo = InetAddress.getByName(groupText);
                if (!grupo.isMulticastAddress()) throw new IOException("Grupo multicast invalido: " + groupText);
                iface = escolherInterface();
                networkInterface = iface;

                atual = new MulticastSocket(null);
                atual.setReuseAddress(true);
                atual.bind(new InetSocketAddress(port));
                atual.setNetworkInterface(iface);
                atual.joinGroup(new InetSocketAddress(grupo, port), iface);
                socket = atual;

                System.out.println("Cliente Desktop inscrito no multicast " + groupText + ":" + port + " via " + iface.getName());
                byte[] buffer = new byte[65507];
                while (running) {
                    DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                    atual.receive(pacote);
                    String linha = new String(pacote.getData(), pacote.getOffset(), pacote.getLength(), StandardCharsets.UTF_8).trim();
                    try {
                        ChatMessage aviso = JsonProtocol.deserializeLine(linha);
                        if (aviso.type() == ChatMessage.Type.ADVERTISEMENT_UPDATE && nomeValido(aviso.content())) {
                            callback.accept(aviso.content());
                        }
                    } catch (Exception ignorado) {
                        System.err.println("Aviso multicast ignorado: JSON invalido.");
                    }
                }
            } catch (Exception erro) {
                if (running) {
                    System.err.println("Publicidade multicast indisponivel no Desktop; nova tentativa em 3s: " + erro.getMessage());
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException interrompido) {
                        Thread.currentThread().interrupt();
                        running = false;
                    }
                }
            } finally {
                socket = null;
                if (atual != null) {
                    try {
                        if (grupo != null && iface != null && !atual.isClosed()) {
                            atual.leaveGroup(new InetSocketAddress(grupo, port), iface);
                        }
                    } catch (Exception ignorado) { }
                    atual.close();
                }
                networkInterface = null;
            }
        }
    }

    private static NetworkInterface escolherInterface() throws IOException {
        String configurada = System.getenv("CHAT_AD_MULTICAST_INTERFACE");
        if (configurada == null || configurada.isBlank()) configurada = System.getenv("CHAT_AD_NETWORK_INTERFACE");
        if (configurada != null && !configurada.isBlank()) {
            String valor = configurada.trim();
            NetworkInterface iface = NetworkInterface.getByName(valor);
            if (iface == null) {
                try { iface = NetworkInterface.getByInetAddress(InetAddress.getByName(valor)); }
                catch (Exception ignored) { }
            }
            if (iface == null || !iface.isUp() || !iface.supportsMulticast()) throw new IOException("Interface multicast nao disponivel: " + configurada);
            return iface;
        }
        NetworkInterface preferida = null;
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (!iface.isUp() || iface.isLoopback() || !iface.supportsMulticast()) continue;
            boolean ipv4 = iface.getInterfaceAddresses().stream().anyMatch(a -> a.getAddress() instanceof java.net.Inet4Address);
            if (!ipv4) continue;
            String nome = (iface.getName() + " " + iface.getDisplayName()).toLowerCase(java.util.Locale.ROOT);
            if (nome.contains("wi-fi") || nome.contains("wifi") || nome.contains("wlan") || nome.contains("wireless")) return iface;
            if (preferida == null) preferida = iface;
        }
        if (preferida == null) throw new IOException("Nenhuma interface de rede com suporte a multicast foi encontrada.");
        return preferida;
    }

    private static boolean nomeValido(String nome) {
        return nome != null && nome.matches("[A-Za-z0-9._-]{1,180}\\.(png|gif|jpg|jpeg|webp)") && !nome.contains("..");
    }

    private static int parsePort(String value, int fallback) {
        try { int port = value == null ? fallback : Integer.parseInt(value.trim()); return port >= 1 && port <= 65535 ? port : fallback; }
        catch (NumberFormatException erro) { return fallback; }
    }

    @Override
    public synchronized void close() {
        running = false;
        MulticastSocket atual = socket;
        socket = null;
        if (atual != null) atual.close();
    }
}
