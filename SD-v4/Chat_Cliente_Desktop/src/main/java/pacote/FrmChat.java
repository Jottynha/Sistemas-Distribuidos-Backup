package pacote;

import protocolo.ChatMessage;
import protocolo.JsonProtocol;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.awt.event.KeyEvent;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.awt.Image;
import javax.swing.JOptionPane;
import javax.net.ssl.SSLSocket;

public class FrmChat extends javax.swing.JFrame {
    private volatile boolean emManutencao = false;
    private volatile boolean envioEmAndamento = false;
    private volatile boolean falhaMonitorRegistrada = false;

    private final JPopupMenu popupEmojis = new JPopupMenu();

    private JPanel painelStickers;

    private String ultimoHistorico = null;

    public String msg = "";
    
    public void gerarEenviarMensagem() {

        if (emManutencao || envioEmAndamento) {
            return;
        }

        String conteudoOriginal = TxtMensagem.getText();

        if (conteudoOriginal == null || conteudoOriginal.isBlank()) {
            return;
        }

        // Montagem do avatar utilizando a URL centralizada.
        this.msg = "<img src='"
                + Util.obterURLAvatar()
                + "' width='20' height='20'>";

        this.msg += "<font color='" + Util.cor + "'>"
                + escaparHtml(Util.nickname)
                + "</font>";

        String texto = escaparHtml(conteudoOriginal);

        texto = substituirEmojisHtml(texto);
        texto = substituirStickersHtml(texto);

        String modo = CBModo.getSelectedItem().toString();

        if (modo.equals("Fala")) {

            this.msg += "<b> Fala: </b>";
            this.msg += texto;

        } else if (modo.equals("Grita")) {

            this.msg += "<b><i><u> Grita: </u></i></b>";
            this.msg += "<font color='Red' size='+1'>"
                    + texto.toUpperCase()
                    + "</font>";

        } else if (modo.equals("Xinga")) {

            this.msg += "<b> Xinga: </b>";
            this.msg += "<font color='Yellow' size='+3'>"
                    + texto.toUpperCase()
                    + "</font>";
        }

        this.msg += "<br>";

        this.msg = this.msg.replace(":-)", "&#128513;");
        this.msg = this.msg.replace(";-)", "&#128521;");
        this.msg = this.msg.replace(":-|", "&#128511;");
        this.msg = this.msg.replace(":-P", "&#128523;");
        this.msg = this.msg.replace(">:)", "&#128520;");
        this.msg = this.msg.replace("B-)", "&#128526;");
        this.msg = this.msg.replace(":-(", "&#128531;");

        // Preserva a mensagem enquanto aguardamos o ACK.
        final String mensagemParaEnviar = this.msg;

        envioEmAndamento = true;

        TxtMensagem.setEnabled(false);
        BtnEnviar.setEnabled(false);
        BtnEmoji.setEnabled(false);

        // Comunicacao de rede fora da EDT do Swing.
        Thread.ofVirtual().start(() -> {

            boolean conexaoEstabelecida = false;

            
            try (SSLSocket cliente =
            ConfiguracaoTLSCliente.conectar(
                    Util.Servidor,
                    Util.PortaRecepcaoJsonTLS
            )) {

            conexaoEstabelecida = true;

            // Monta a mensagem no protocolo JSON.
            ChatMessage requisicao = new ChatMessage(
                    ChatMessage.CURRENT_VERSION,
                    null,
                    null,
                    ChatMessage.Type.SEND_MESSAGE,
                    Util.nickname,
                    "Todos",
                    conteudoOriginal,
                    modo.toUpperCase(Locale.ROOT),
                    Util.cor,
                    Util.avatar,
                    "",
                    Instant.now().toString(),
                    Map.of(
                            "clientType", "desktop-java"
                    )
            );

            // Serializa e envia a mensagem.
            BufferedWriter output = new BufferedWriter(
                    new OutputStreamWriter(
                            cliente.getOutputStream(),
                            StandardCharsets.UTF_8
                    )
            );

            output.write(JsonProtocol.serializeLine(requisicao));
            output.flush();

            // Recebe a resposta JSON do middleware.
            BufferedReader input = new BufferedReader(
                    new InputStreamReader(
                            cliente.getInputStream(),
                            StandardCharsets.UTF_8
                    )
            );

            String respostaJson = input.readLine();

            if (respostaJson == null || respostaJson.isBlank()) {
                throw new IOException(
                        "Middleware encerrou a conexao sem responder."
                );
            }

            ChatMessage resposta =
                    JsonProtocol.deserializeLine(respostaJson);

            Object status = resposta.metadata().get("status");

            if (resposta.type() == ChatMessage.Type.ACK
                    && "SUCCESS".equalsIgnoreCase(
                            String.valueOf(status))) {

                SwingUtilities.invokeLater(() -> {
                    TxtMensagem.setText("");
                });

            } else {

                String detalhe = resposta.content();

                throw new IOException(
                        "Middleware nao confirmou a mensagem: "
                        + detalhe
                );
            }
        } catch (Exception e) {
                final boolean conectouAoServidor = conexaoEstabelecida;
                reportarErro(
                        "gerarEenviarMensagem",
                        e
                );
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(
                            this,
                            "Nao foi possivel confirmar o envio.\n"
                            + "A mensagem foi mantida no campo para "
                            + "que voce possa tentar novamente.",
                            "Erro de comunicacao",
                            JOptionPane.ERROR_MESSAGE
                    );
                    if (!conectouAoServidor) {
                        mostrarManutencao();
                    }
                });

            } finally {

                SwingUtilities.invokeLater(() -> {

                    envioEmAndamento = false;

                    if (!emManutencao) {

                        TxtMensagem.setEnabled(true);
                        BtnEnviar.setEnabled(true);
                        BtnEmoji.setEnabled(true);
                    }
                });
            }
        });
    }
    private String escaparHtml(String texto) {

        if (texto == null) {
            return "";
        }

        return texto
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
    
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(FrmChat.class.getName());
    public FrmChat() {
        try {

            ConfiguracaoTLSCliente.inicializar();

        } catch (IOException erro) {

            reportarErro("inicializarTLS", erro);

            JOptionPane.showMessageDialog(
                    null,
                    "Nao foi possivel inicializar a confianca TLS.\n"
                    + erro.getMessage(),
                    "Erro de seguranca",
                    JOptionPane.ERROR_MESSAGE
            );
        }

        initComponents();
        configurarPopupEmojis();
        iniciarMonitorServidor();
    }
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        ScrConversa = new javax.swing.JScrollPane();
        EdtConversa = new javax.swing.JEditorPane();
        LblMensagem = new javax.swing.JLabel();
        TxtMensagem = new javax.swing.JTextField();
        LblModo = new javax.swing.JLabel();
        CBModo = new javax.swing.JComboBox<>();
        BtnEnviar = new javax.swing.JButton();
        BtnEmoji = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Chat");

        EdtConversa.setEditable(false);
        EdtConversa.setContentType("text/html"); // NOI18N
        ScrConversa.setViewportView(EdtConversa);

        LblMensagem.setFont(new java.awt.Font("Liberation Sans", 1, 18)); // NOI18N
        LblMensagem.setText("Mensagem");

        TxtMensagem.setToolTipText("");
        TxtMensagem.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                TxtMensagemKeyPressed(evt);
            }
        });

        LblModo.setFont(new java.awt.Font("Liberation Sans", 1, 18)); // NOI18N
        LblModo.setText("Modo:");

        CBModo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Fala", "Grita", "Xinga" }));

        BtnEnviar.setFont(new java.awt.Font("Liberation Sans", 1, 18)); // NOI18N
        BtnEnviar.setText("ENVIAR");
        BtnEnviar.addActionListener(this::BtnEnviarActionPerformed);

        BtnEmoji.setText("😊 ");
        BtnEmoji.addActionListener(this::BtnEmojiActionPerformed);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(ScrConversa)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(LblModo)
                            .addComponent(LblMensagem))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(CBModo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(167, 167, 167)
                                .addComponent(BtnEnviar, javax.swing.GroupLayout.PREFERRED_SIZE, 175, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(TxtMensagem, javax.swing.GroupLayout.PREFERRED_SIZE, 483, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(BtnEmoji)))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(ScrConversa, javax.swing.GroupLayout.PREFERRED_SIZE, 320, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(LblMensagem)
                    .addComponent(TxtMensagem, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(BtnEmoji))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(2, 2, 2)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(LblModo)
                            .addComponent(CBModo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(BtnEnviar, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(85, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void BtnEnviarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BtnEnviarActionPerformed
        this.gerarEenviarMensagem();
    }//GEN-LAST:event_BtnEnviarActionPerformed

    private void TxtMensagemKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_TxtMensagemKeyPressed
        if(evt.getKeyCode() == KeyEvent.VK_ENTER){
            this.gerarEenviarMensagem();
        }
    }//GEN-LAST:event_TxtMensagemKeyPressed

    private void BtnEmojiActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BtnEmojiActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_BtnEmojiActionPerformed

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(() -> new FrmChat().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton BtnEmoji;
    private javax.swing.JButton BtnEnviar;
    private javax.swing.JComboBox<String> CBModo;
    private javax.swing.JEditorPane EdtConversa;
    private javax.swing.JLabel LblMensagem;
    private javax.swing.JLabel LblModo;
    private javax.swing.JScrollPane ScrConversa;
    private javax.swing.JTextField TxtMensagem;
    // End of variables declaration//GEN-END:variables
    
    private void configurarPopupEmojis() {

        JTabbedPane abasPrincipal = new JTabbedPane();
        JTabbedPane abasEmojis = new JTabbedPane();

        abasEmojis.addTab(
                "Emojis",
                criarGradeEmojis(carregarEmojisServidor())
        );

        JPanel painelEmojis = new JPanel(new BorderLayout());

        painelEmojis.add(
                abasEmojis,
                BorderLayout.CENTER
        );

        painelStickers = new JPanel(new BorderLayout());

        String[] stickersPadrao = {
            "fig1.gif",
            "fig2.gif",
            "beijo.png",
            "coracao.png",
            "dinheiro.png"
        };

        painelStickers.add(
                criarGradeStickers(stickersPadrao),
                BorderLayout.CENTER
        );

        abasPrincipal.addTab("😊 Emojis", painelEmojis);
        abasPrincipal.addTab("🖼 Stickers", painelStickers);

        abasPrincipal.setPreferredSize(
                new Dimension(380, 260)
        );

        popupEmojis.setLayout(new BorderLayout());

        popupEmojis.add(
                abasPrincipal,
                BorderLayout.CENTER
        );

        BtnEmoji.addActionListener(
                e -> popupEmojis.show(
                        BtnEmoji,
                        0,
                        BtnEmoji.getHeight()
                )
        );

        // Atualiza a lista com as figurinhas centralizadas.
        carregarStickersDoServidor();
    }
    
    private String[] carregarEmojisServidor() {

        String[] emojisPadrao = {
            "😀", "😁", "😂", "🤣", "😅", "😊",
            "😍", "😎", "😘", "😜", "😢", "😭",
            "😡", "😱", "🤔", "😴", "🥳", "🤩",
            "👍", "👎", "👏", "🙏", "❤️", "💔",
            "🔥", "🎉", "💯", "🤝", "👀", "💀"
        };

        java.net.HttpURLConnection conexao = null;

        try {

            String endereco = Util.URL_API_STICKERS.replace(
                    "/api/stickers",
                    "/api/emojis"
            );

            conexao = (java.net.HttpURLConnection)
                    new java.net.URL(endereco).openConnection();

            conexao.setConnectTimeout(2000);
            conexao.setReadTimeout(3000);

            if (conexao.getResponseCode() != 200) {
                return emojisPadrao;
            }

            String resposta;

            try (var entrada = conexao.getInputStream()) {

                resposta = new String(
                        entrada.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8
                );
            }

            String[] encontrados = resposta.lines()
                    .filter(emoji -> !emoji.isBlank())
                    .toArray(String[]::new);

            return encontrados.length > 0
                    ? encontrados
                    : emojisPadrao;

        } catch (Exception erro) {

            System.err.println(
                    "Nao foi possivel carregar emojis do servidor: "
                    + erro.getMessage()
            );

            return emojisPadrao;

        } finally {

            if (conexao != null) {
                conexao.disconnect();
            }
        }
    }


    private JPanel criarGradeEmojis(String[] emojis) {
        JPanel painel = new JPanel(new GridLayout(0, 6, 4, 4));
        painel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        for (String emoji : emojis) {
            JButton btn = new JButton(emoji);
            btn.setFont(new Font("Dialog", Font.PLAIN, 20));
            btn.setMargin(new Insets(3, 3, 3, 3));
            btn.setFocusPainted(false);
            btn.setToolTipText(emoji);
            btn.addActionListener(e ->inserirEmojiNoTexto(emoji));
            painel.add(btn);
        }
        return painel;
    }
    private void inserirEmojiNoTexto(String emoji) {
        int posicao = TxtMensagem.getCaretPosition();
        String texto = TxtMensagem.getText();
        TxtMensagem.setText(texto.substring(0, posicao) + emoji + texto.substring(posicao));
        TxtMensagem.setCaretPosition(posicao + emoji.length());
        TxtMensagem.requestFocusInWindow();
        popupEmojis.setVisible(false);
    }
    private String substituirEmojisHtml(String texto) {
        return texto
            .replace("😀", "&#128512;")
            .replace("😁", "&#128513;")
            .replace("😂", "&#128514;")
            .replace("😅", "&#128517;")
            .replace("😊", "&#128522;")
            .replace("😍", "&#128525;")
            .replace("😎", "&#128526;")
            .replace("😘", "&#128536;")
            .replace("😜", "&#128539;")
            .replace("😢", "&#128546;")
            .replace("😡", "&#128545;")
            .replace("😱", "&#128561;");
    }
    
    private JPanel criarGradeStickers(String[] stickers) {

        JPanel painel = new JPanel(
                new GridLayout(0, 4, 4, 4)
        );

        painel.setBorder(
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        );

        for (String sticker : stickers) {

            JButton btn = new JButton(sticker);

            btn.setFocusPainted(false);
            btn.setToolTipText(sticker);

            btn.setPreferredSize(
                    new Dimension(80, 65)
            );

            btn.addActionListener(
                    e -> inserirStickerNoTexto(sticker)
            );

            painel.add(btn);

            carregarImagemSticker(btn, sticker);
        }

        return painel;
    }
    
    
    private void carregarImagemSticker(
            JButton botao,
            String nomeArquivo) {

        Thread.ofVirtual().start(() -> {

            String url = obterUrlFigurinha(nomeArquivo);

            if (url == null) {
                return;
            }

            HttpURLConnection conexao = null;

            try {

                conexao = (HttpURLConnection)
                        new URL(url).openConnection();

                conexao.setConnectTimeout(3000);
                conexao.setReadTimeout(5000);

                byte[] dados;

                try (var entrada = conexao.getInputStream()) {
                    dados = entrada.readAllBytes();
                }

                javax.swing.ImageIcon original =
                        new javax.swing.ImageIcon(dados);

                if (original.getIconWidth() <= 0) {
                    return;
                }

                Image imagem = original.getImage()
                        .getScaledInstance(
                                48,
                                48,
                                Image.SCALE_SMOOTH
                        );

                javax.swing.ImageIcon icone =
                        new javax.swing.ImageIcon(imagem);

                SwingUtilities.invokeLater(() -> {

                    botao.setIcon(icone);
                    botao.setText("");
                });

            } catch (Exception e) {

                // Mantem o nome do arquivo no botao
                // caso a imagem nao esteja disponivel.

            } finally {

                if (conexao != null) {
                    conexao.disconnect();
                }
            }
        });
    }
    
    
    private void carregarStickersDoServidor() {

        Thread.ofVirtual().start(() -> {

            HttpURLConnection conexao = null;

            try {

                conexao = (HttpURLConnection)
                        new URL(Util.URL_API_STICKERS)
                                .openConnection();

                conexao.setConnectTimeout(3000);
                conexao.setReadTimeout(5000);

                if (conexao.getResponseCode() != 200) {
                    return;
                }

                String resposta;

                try (var entrada = conexao.getInputStream()) {

                    resposta = new String(
                            entrada.readAllBytes(),
                            StandardCharsets.UTF_8
                    );
                }

                String[] stickers = resposta.lines()
                        .map(String::trim)
                        .filter(nome -> !nome.isBlank())
                        .filter(nome -> !nome.contains(".."))
                        .filter(nome -> !nome.contains("/"))
                        .filter(nome -> !nome.contains("\\"))
                        .toArray(String[]::new);

                if (stickers.length == 0) {
                    return;
                }

                SwingUtilities.invokeLater(() -> {

                    painelStickers.removeAll();

                    painelStickers.add(
                            criarGradeStickers(stickers),
                            BorderLayout.CENTER
                    );

                    painelStickers.revalidate();
                    painelStickers.repaint();
                });

            } catch (Exception e) {

                // Se o servidor estiver indisponivel,
                // permanece a lista padrao de figurinhas.

            } finally {

                if (conexao != null) {
                    conexao.disconnect();
                }
            }
        });
    }
    
    
    private String obterUrlFigurinha(String nomeArquivo) {

        if (nomeArquivo == null
                || nomeArquivo.isBlank()
                || nomeArquivo.contains("..")
                || nomeArquivo.contains("/")
                || nomeArquivo.contains("\\")) {

            return null;
        }

        String nomeCodificado = URLEncoder.encode(
                nomeArquivo,
                StandardCharsets.UTF_8
        ).replace("+", "%20");

        return Util.URL_FIGURINHAS + nomeCodificado;
    }




    private void inserirStickerNoTexto(String sticker) {
        String codigoSticker = "(sticker:" + sticker + ")";
        int posicao = TxtMensagem.getCaretPosition();
        String texto = TxtMensagem.getText();
        TxtMensagem.setText(texto.substring(0, posicao) + codigoSticker + texto.substring(posicao));
        TxtMensagem.setCaretPosition(posicao + codigoSticker.length());
        TxtMensagem.requestFocusInWindow();
        popupEmojis.setVisible(false);
    }
    
    private String substituirStickersHtml(String texto) {

        java.util.regex.Pattern pattern =
                java.util.regex.Pattern.compile(
                        "\\(sticker:([^)]*)\\)"
                );

        java.util.regex.Matcher matcher =
                pattern.matcher(texto);

        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {

            String nomeArquivo = matcher.group(1).trim();

            String url = obterUrlFigurinha(nomeArquivo);

            String substituicao;

            if (url == null) {

                substituicao = matcher.group(0);

            } else {

                substituicao = "<img src='"
                        + url
                        + "' width='60' height='60'>";
            }

            matcher.appendReplacement(
                    sb,
                    java.util.regex.Matcher.quoteReplacement(
                            substituicao
                    )
            );
        }

        matcher.appendTail(sb);

        return sb.toString();
    }

    
    private void reportarErro(String metodo, Throwable erro) {

        Thread.ofVirtual().start(() -> {

            HttpURLConnection conexao = null;

            try {

                String computador =
                        InetAddress.getLocalHost().getHostName();

                String mensagem = erro.getClass().getSimpleName()
                        + ": "
                        + (erro.getMessage() == null
                                ? "Sem detalhes"
                                : erro.getMessage());

                mensagem = mensagem
                        .replace('\r', ' ')
                        .replace('\n', ' ')
                        .replace('\t', ' ');

                String registro =
                        "desktop"
                        + "\t" + computador
                        + "\t" + "FrmChat:" + metodo
                        + "\t" + mensagem;

                byte[] dados = registro.getBytes(
                        StandardCharsets.UTF_8
                );

                conexao = (HttpURLConnection)
                        new URL(Util.URL_COLETOR_LOGS)
                                .openConnection();

                conexao.setRequestMethod("POST");
                conexao.setDoOutput(true);

                conexao.setConnectTimeout(3000);
                conexao.setReadTimeout(5000);

                conexao.setRequestProperty(
                        "Content-Type",
                        "text/plain; charset=UTF-8"
                );

                conexao.setFixedLengthStreamingMode(dados.length);

                try (var saida = conexao.getOutputStream()) {
                    saida.write(dados);
                    saida.flush();
                }

                int resposta = conexao.getResponseCode();

                if (resposta != 200) {

                    System.err.println(
                            "Coletor de logs retornou HTTP "
                            + resposta
                    );
                }

            } catch (Exception falhaLog) {

                /*
                 * Nao tenta registrar novamente essa excecao,
                 * evitando um ciclo de erros.
                 */
                System.err.println(
                        "Nao foi possivel enviar o log: "
                        + falhaLog.getMessage()
                );

            } finally {

                if (conexao != null) {
                    conexao.disconnect();
                }
            }
        });
    }

    
    private void iniciarMonitorServidor() {

        Thread.ofVirtual()
                .name("desktop-monitor")
                .start(() -> {

            while (!Thread.currentThread().isInterrupted()) {

                try (SSLSocket cliente =
                    ConfiguracaoTLSCliente.conectar(
                            Util.Servidor,
                            Util.PortaRecepcaoJsonTLS
                    )) {

        String mensagens;

        // Monta a requisicao JSON de historico.
        ChatMessage requisicao = new ChatMessage(

                ChatMessage.CURRENT_VERSION,

                null,

                null,

                ChatMessage.Type.HISTORY_REQUEST,

                Util.nickname,

                "Todos",

                "",

                "", "", "", "",

                Instant.now().toString(),

                Map.of(
                        "clientType", "desktop-java"
                )
        );

        // Envia a requisicao e recebe a resposta JSON.
        try (
                BufferedWriter output = new BufferedWriter(
                        new OutputStreamWriter(
                                cliente.getOutputStream(),
                                StandardCharsets.UTF_8
                        )
                );

                BufferedReader input = new BufferedReader(
                        new InputStreamReader(
                                cliente.getInputStream(),
                                StandardCharsets.UTF_8
                        )
                )
        ) {

            output.write(JsonProtocol.serializeLine(requisicao));
            output.flush();

            String linhaResposta = input.readLine();

            if (linhaResposta == null || linhaResposta.isBlank()) {
                throw new IOException(
                        "Middleware encerrou a conexao sem responder ao historico."
                );
            }

            ChatMessage resposta =
                    JsonProtocol.deserializeLine(linhaResposta);

            if (resposta.type() != ChatMessage.Type.HISTORY_RESPONSE) {
                throw new IOException(
                        "Resposta inesperada do middleware: "
                        + resposta.content()
                );
            }

            Object status = resposta.metadata().get("status");

            if (!"SUCCESS".equalsIgnoreCase(String.valueOf(status))) {
                throw new IOException(
                        "Falha ao consultar historico."
                );
            }

            mensagens = resposta.content();
        }

                    falhaMonitorRegistrada = false;

                    SwingUtilities.invokeLater(() -> {

                        emManutencao = false;

                        TxtMensagem.setEnabled(!envioEmAndamento);
                        BtnEnviar.setEnabled(!envioEmAndamento);
                        BtnEmoji.setEnabled(!envioEmAndamento);

                        // Atualiza a conversa apenas quando houver
                        // mudanca no historico.
                        if (!java.util.Objects.equals(
                                ultimoHistorico,
                                mensagens)) {

                            ultimoHistorico = mensagens;

                            EdtConversa.setText("");

                            if (mensagens != null
                                    && !mensagens.isBlank()) {

                                HTMLDocument doc =
                                        (HTMLDocument)
                                        EdtConversa.getDocument();

                                HTMLEditorKit kit =
                                        (HTMLEditorKit)
                                        EdtConversa.getEditorKit();

                                try {
                                    doc.setBase(
                                            new URL(
                                                    "https",
                                                    Util.Servidor,
                                                    Util.PortaWebTLS,
                                                    "/"
                                            )
                                    );
                                    kit.insertHTML(
                                            doc,
                                            doc.getLength(),
                                            mensagens,
                                            0,
                                            0,
                                            null
                                    );

                                } catch (Exception e) {

                                    reportarErro(
                                            "atualizarConversa",
                                            e
                                    );

                                    EdtConversa.setText(mensagens);
                                }
                            }
                        }
                    });

                    Thread.sleep(2000);

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();
                    break;

                } catch (Exception e) {

                    // Evita registrar centenas de erros iguais
                    // enquanto o servidor estiver indisponivel.
                    if (!falhaMonitorRegistrada) {

                        falhaMonitorRegistrada = true;

                        reportarErro(
                                "iniciarMonitorServidor",
                                e
                        );
                    }

                    mostrarManutencao();

                    try {

                        Thread.sleep(1500);

                    } catch (InterruptedException ex) {

                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }

    
    private void mostrarManutencao() {

        SwingUtilities.invokeLater(() -> {

            if (!emManutencao) {

                emManutencao = true;

                ultimoHistorico = null;

                TxtMensagem.setEnabled(false);
                BtnEnviar.setEnabled(false);
                BtnEmoji.setEnabled(false);

                EdtConversa.setText(
                        "<html><body style='text-align:center;'>"
                        + "<br><br>"
                        + "<h2>Servidor em manutencao</h2>"
                        + "<p>O servico de chat esta temporariamente "
                        + "indisponivel.</p>"
                        + "<p>Tentando reconectar...</p>"
                        + "</body></html>"
                );
            }
        });
    }

}
