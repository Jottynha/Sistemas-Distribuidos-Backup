package pacote;

import java.awt.event.KeyEvent;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
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

public class FrmChat extends javax.swing.JFrame {
    private volatile boolean emManutencao = false; // volátil -> thread de recepção está sendo executada separadamente.
    private final JPopupMenu popupEmojis = new JPopupMenu();
    public String msg = "";
    private final String STICKER_BASE_PATH = "file:/home/joao/Projetos/Sistemas-Distribuidos/SD-v4/Chat_Cliente_Desktop/src/main/java/images/";
    public void gerarEenviarMensagem() {
        if (emManutencao) {
            return;
        }
        this.msg = "";
        this.msg += "<img src='file:" + Util.avatar + "' width='20' height='20'>";
        this.msg += "<font color='" + Util.cor + "'>" + Util.nickname + "</font>";
        String texto = substituirEmojisHtml(TxtMensagem.getText());
        texto = substituirStickersHtml(texto);
        if (CBModo.getSelectedItem().toString().equals("Fala")) {
            this.msg += "<b> Fala: </b>";
            this.msg += texto;
        } else if (
            CBModo.getSelectedItem().toString().equals("Grita")
        ) {
            this.msg += "<b><i><u> Grita: </b></i></u>";
            this.msg += "<font color='Red' size='+1'>" + texto.toUpperCase() + "</font>";
        } else if (
            CBModo.getSelectedItem().toString().equals("Xinga")
        ) {
            this.msg += "<b> Xinga: </b>";
            this.msg += "<font color='Yellow' size='+3'>" + texto.toUpperCase() + "</font>";
        }
        this.msg += "<br>";
        this.msg = this.msg.replace(":-)", "&#128513;");
        this.msg = this.msg.replace(";-)", "&#128521;");
        this.msg = this.msg.replace(":-|", "&#128511;");
        this.msg = this.msg.replace(":-P", "&#128523;");
        this.msg = this.msg.replace(">:)", "&#128520;");
        this.msg = this.msg.replace("B-)", "&#128526;");
        this.msg = this.msg.replace(":-(", "&#128531;");
        try {
            Socket cliente = new Socket("127.0.0.1", 6662);
            ObjectOutputStream output = new ObjectOutputStream(cliente.getOutputStream());
            output.writeUTF(this.msg);
            output.close();
            cliente.close();
            TxtMensagem.setText("");
        } catch (Exception e) {
            mostrarManutencao();
        }
    }
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(FrmChat.class.getName());
    public FrmChat() {
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
        abasEmojis.addTab("Rostos",criarGradeEmojis(new String[]{
                "😀", "😁", "😂", "😅", "😊", "😍",
                "😎", "😘", "😜", "😢", "😡", "😱"
            })
        );
        JPanel painelEmojis = new JPanel(new BorderLayout());
        painelEmojis.add(abasEmojis,BorderLayout.CENTER);
        JPanel painelStickers = new JPanel(new BorderLayout());
        String[] meusStickers = {"fig1.gif", "fig2.gif", "Beijo.png","Coração.png","Dinheiro.png"}; 
        painelStickers.add(criarGradeStickers(meusStickers), BorderLayout.CENTER);
        abasPrincipal.addTab("😊 Emojis",painelEmojis);
        abasPrincipal.addTab("🖼Stickers",painelStickers);
        abasPrincipal.setPreferredSize(new Dimension(380, 260));
        popupEmojis.setLayout(new BorderLayout());
        popupEmojis.add(abasPrincipal,BorderLayout.CENTER);
        BtnEmoji.addActionListener(e ->popupEmojis.show(BtnEmoji,0,BtnEmoji.getHeight()));
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
        JPanel painel = new JPanel(new GridLayout(0, 4, 4, 4));
        painel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        for (String sticker : stickers) {
            JButton btn = new JButton();
            try {
                java.net.URL url = new java.net.URL(STICKER_BASE_PATH + sticker);
                btn.setIcon(new javax.swing.ImageIcon(url));
            } catch (Exception e) {
                btn.setText(sticker); 
            }
            btn.setFocusPainted(false);
            btn.setToolTipText(sticker);
            btn.addActionListener(e -> inserirStickerNoTexto(sticker));
            painel.add(btn);
        }
        return painel;
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
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\(sticker:(.*?)\\)");
        java.util.regex.Matcher matcher = pattern.matcher(texto);
        StringBuffer sb = new StringBuffer();   
        while (matcher.find()) {
            String nomeArquivo = matcher.group(1);
            String tagImg = "<img src='" + STICKER_BASE_PATH + nomeArquivo + "' width='60' height='60'>";
            matcher.appendReplacement(sb, tagImg);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
    private void iniciarMonitorServidor() {
        Thread.ofVirtual().start(() -> {
            while (true) {
                try {
                    Socket cliente = new Socket("127.0.0.1",Util.PortaEnvioDesktop);
                    ObjectInputStream input = new ObjectInputStream(cliente.getInputStream());
                    String msgs = input.readUTF();
                    input.close();
                    cliente.close();
                    SwingUtilities.invokeLater(() -> {
                        emManutencao = false;
                        TxtMensagem.setEnabled(true);
                        BtnEnviar.setEnabled(true);
                        BtnEmoji.setEnabled(true);
                        EdtConversa.setText("");
                        if (msgs != null && !msgs.isBlank()) {
                            HTMLDocument doc = (HTMLDocument) EdtConversa.getDocument();
                            HTMLEditorKit kit = (HTMLEditorKit) EdtConversa.getEditorKit();
                            try {
                                kit.insertHTML(doc,doc.getLength(),msgs,0,0,null);
                            } catch (Exception e) {
                                EdtConversa.setText(msgs);
                            }
                        }
                    });
                    Thread.sleep(1000);
                } catch (Exception e) {
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
                TxtMensagem.setEnabled(false);
                BtnEnviar.setEnabled(false);
                BtnEmoji.setEnabled(false);
                EdtConversa.setText( "<html> <body style='text-align:center;'> <br><br>"
                    + "<h2>Servidor em manutenção</h2> <p>O serviço de chat está temporariamente indisponível.</p>"
                    + "<p>Tentando reconectar...</p>"
                    + "</body> </html>"
                );
            }
        });
    }
}
