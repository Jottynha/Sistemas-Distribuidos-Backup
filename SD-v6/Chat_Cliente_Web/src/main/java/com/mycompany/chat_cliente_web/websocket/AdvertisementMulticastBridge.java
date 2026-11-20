package com.mycompany.chat_cliente_web.websocket;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import java.io.StringReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Recebe UDP multicast e retransmite avisos ao navegador por WebSocket. */
@ServerEndpoint("/ws/publicidade")
public class AdvertisementMulticastBridge {
    private static final Set<Session> SESSIONS = ConcurrentHashMap.newKeySet();
    private static volatile boolean receiverRunning;
    private static volatile MulticastSocket multicastSocket;
    private static volatile Thread receiverThread;

    @OnOpen
    public void onOpen(Session session) {
        SESSIONS.add(session);
        iniciarReceptorSeNecessario();
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        SESSIONS.remove(session);
        pararReceptorSeSemClientes();
    }

    @OnError
    public void onError(Session session, Throwable erro) {
        if (session != null) SESSIONS.remove(session);
        if (erro != null) System.err.println("WebSocket publicidade: " + erro.getMessage());
        pararReceptorSeSemClientes();
    }

    private static synchronized void iniciarReceptorSeNecessario() {
        if (receiverRunning && receiverThread != null && receiverThread.isAlive()) return;
        receiverRunning = true;
        receiverThread = new Thread(AdvertisementMulticastBridge::loopRecepcao,
                "glassfish-publicidade-multicast-bridge");
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    private static synchronized void pararReceptorSeSemClientes() {
        if (!SESSIONS.isEmpty()) return;
        receiverRunning = false;
        MulticastSocket socket = multicastSocket;
        multicastSocket = null;
        if (socket != null) socket.close();
        Thread thread = receiverThread;
        if (thread != null) thread.interrupt();
        receiverThread = null;
    }

    private static void loopRecepcao() {
        String grupoTexto = System.getenv().getOrDefault("CHAT_AD_MULTICAST_GROUP", "239.255.42.99").trim();
        int porta = parsePort(System.getenv("CHAT_AD_MULTICAST_PORT"), 5005);

        while (receiverRunning && !Thread.currentThread().isInterrupted()) {
            try {
                InetAddress grupo = InetAddress.getByName(grupoTexto);
                if (!grupo.isMulticastAddress()) throw new IllegalArgumentException("Grupo multicast invalido: " + grupoTexto);
                NetworkInterface iface = selecionarInterface(System.getenv("CHAT_AD_MULTICAST_INTERFACE"));

                try (MulticastSocket socket = new MulticastSocket(null)) {
                    socket.setReuseAddress(true);
                    socket.bind(new InetSocketAddress(porta));
                    socket.joinGroup(new InetSocketAddress(grupo, porta), iface);
                    multicastSocket = socket;
                    System.out.println("Gateway Web inscrito no multicast " + grupoTexto + ":" + porta
                            + " pela interface " + iface.getName());

                    byte[] buffer = new byte[65507];
                    while (receiverRunning && !Thread.currentThread().isInterrupted()) {
                        java.net.DatagramPacket packet = new java.net.DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        String payload = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                        if (avisoValido(payload)) retransmitir(payload);
                    }
                    try { socket.leaveGroup(new InetSocketAddress(grupo, porta), iface); }
                    catch (Exception ignored) { }
                    multicastSocket = null;
                }
            } catch (Exception erro) {
                if (receiverRunning) {
                    System.err.println("Gateway Web nao conseguiu receber multicast: " + erro.getMessage());
                    try { Thread.sleep(2500); }
                    catch (InterruptedException interrompido) { Thread.currentThread().interrupt(); }
                }
            }
        }
    }

    private static boolean avisoValido(String payload) {
        try (JsonReader reader = Json.createReader(new StringReader(payload))) {
            JsonObject json = reader.readObject();
            if (!"ADVERTISEMENT_UPDATE".equals(json.getString("type", ""))) return false;
            String nome = json.getString("content", "");
            return nome.matches("[A-Za-z0-9._-]{1,180}\\.(png|gif|jpg|jpeg|webp)") && !nome.contains("..");
        } catch (Exception erro) {
            return false;
        }
    }

    private static void retransmitir(String payload) {
        for (Session session : SESSIONS) {
            if (session == null || !session.isOpen()) {
                SESSIONS.remove(session);
                continue;
            }
            try {
                session.getAsyncRemote().sendText(payload);
            } catch (Exception erro) {
                SESSIONS.remove(session);
                try { session.close(); } catch (Exception ignored) { }
            }
        }
    }

    private static NetworkInterface selecionarInterface(String configurada) throws Exception {
        if (configurada != null && !configurada.isBlank()) {
            String valor = configurada.trim();
            NetworkInterface iface = NetworkInterface.getByName(valor);
            if (iface == null) {
                try { iface = NetworkInterface.getByInetAddress(InetAddress.getByName(valor)); }
                catch (Exception ignored) { }
            }
            if (iface == null || !iface.isUp() || !iface.supportsMulticast()) {
                throw new IllegalStateException("CHAT_AD_MULTICAST_INTERFACE nao identifica uma interface multicast ativa: " + valor);
            }
            return iface;
        }

        NetworkInterface alternativa = null;
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (!iface.isUp() || iface.isLoopback() || !iface.supportsMulticast()) continue;
            boolean ipv4 = iface.getInterfaceAddresses().stream()
                    .anyMatch(endereco -> endereco.getAddress() instanceof java.net.Inet4Address);
            if (!ipv4) continue;
            String nomes = (iface.getName() + " " + iface.getDisplayName()).toLowerCase(java.util.Locale.ROOT);
            if (nomes.contains("wi-fi") || nomes.contains("wifi") || nomes.contains("wlan") || nomes.contains("wireless")) return iface;
            if (alternativa == null) alternativa = iface;
        }
        if (alternativa == null) throw new IllegalStateException("Nenhuma interface IPv4 com multicast foi encontrada no GlassFish.");
        return alternativa;
    }

    private static int parsePort(String value, int fallback) {
        try {
            int port = value == null ? fallback : Integer.parseInt(value.trim());
            return port >= 1 && port <= 65535 ? port : fallback;
        } catch (NumberFormatException erro) {
            return fallback;
        }
    }
}
