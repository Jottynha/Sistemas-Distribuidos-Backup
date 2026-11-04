package pacote;

import javax.swing.JOptionPane;

/**
 *
 * @author joao
 */
public class FrmLogin extends javax.swing.JFrame {
    private final javax.swing.JComboBox<String> CbAvataresServidor =
        new javax.swing.JComboBox<>(
                new String[]{
                    "lanterna-verde.png",
                    "batman.png",
                    "flash.png",
                    "menino.png",
                    "menina.png",
                    "indefinido.png"
                }
        );
    private final javax.swing.JLabel LblPreviewAvatar =
            new javax.swing.JLabel("Prévia", javax.swing.SwingConstants.CENTER);

    private final java.util.concurrent.atomic.AtomicLong requisicaoPreviewAvatar =
            new java.util.concurrent.atomic.AtomicLong();

    private volatile boolean atualizandoCatalogoAvatares = false;
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(FrmLogin.class.getName());
    public FrmLogin() {

        initComponents();

        configurarSeletorAvatares();

        carregarAvataresServidor();
    }

    private void configurarSeletorAvatares() {
        RadAvatar1.setVisible(false);
        RadAvatar2.setVisible(false);
        RadAvatar3.setVisible(false);
        LblAvatar1.setVisible(false);
        LblAvatar2.setVisible(false);
        LblAvatar3.setVisible(false);

        // Elimina as referências aos caminhos absolutos antigos do initComponents.
        LblAvatar1.setIcon(null);
        LblAvatar2.setIcon(null);
        LblAvatar3.setIcon(null);

        CbAvataresServidor.setBounds(110, 88, 190, 30);
        CbAvataresServidor.setToolTipText("Selecione seu avatar");
        CbAvataresServidor.addActionListener(e -> {
            if (!atualizandoCatalogoAvatares) atualizarPreviewAvatar();
        });
        getContentPane().add(CbAvataresServidor);

        LblPreviewAvatar.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        LblPreviewAvatar.setVerticalAlignment(javax.swing.SwingConstants.CENTER);
        LblPreviewAvatar.setOpaque(true);
        LblPreviewAvatar.setBackground(java.awt.Color.WHITE);
        LblPreviewAvatar.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(new java.awt.Color(218, 224, 238)),
                javax.swing.BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));
        LblPreviewAvatar.setToolTipText("Pré-visualização do avatar selecionado");
        LblPreviewAvatar.setBounds(320, 70, 76, 76);
        getContentPane().add(LblPreviewAvatar);

        setMinimumSize(new java.awt.Dimension(430, 300));
        setSize(new java.awt.Dimension(430, 300));
        setResizable(false);
        setLocationRelativeTo(null);
    }

    
    private void carregarAvataresServidor() {
        Thread.ofVirtual().start(() -> {
            java.net.HttpURLConnection conexao = null;
            try {
                String endereco = Util.URL_API_STICKERS.replace(
                        "/api/stickers", "/api/avatars"
                );
                conexao = (java.net.HttpURLConnection)
                        new java.net.URL(endereco).openConnection();
                conexao.setConnectTimeout(3000);
                conexao.setReadTimeout(5000);

                if (conexao.getResponseCode() != 200) {
                    throw new java.io.IOException("HTTP " + conexao.getResponseCode());
                }

                String resposta;
                try (var entrada = conexao.getInputStream()) {
                    resposta = new String(entrada.readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8);
                }

                String[] avatares = resposta.lines()
                        .map(String::trim)
                        .filter(nome -> nome.matches(
                                "[a-zA-Z0-9._-]+\\\\.(png|gif|jpg|jpeg|webp)"
                        ))
                        .filter(nome -> !nome.contains(".."))
                        .toArray(String[]::new);

                if (avatares.length == 0) {
                    throw new java.io.IOException("O servidor não retornou avatares.");
                }

                javax.swing.SwingUtilities.invokeLater(() -> {
                    String selecionado = (String) CbAvataresServidor.getSelectedItem();
                    atualizandoCatalogoAvatares = true;
                    try {
                        CbAvataresServidor.removeAllItems();
                        for (String avatar : avatares) CbAvataresServidor.addItem(avatar);
                        if (selecionado != null) CbAvataresServidor.setSelectedItem(selecionado);
                    } finally {
                        atualizandoCatalogoAvatares = false;
                    }
                    atualizarPreviewAvatar();
                });
            } catch (Exception erro) {
                System.err.println("Não foi possível carregar avatares remotos: " + erro.getMessage());
                javax.swing.SwingUtilities.invokeLater(this::atualizarPreviewAvatar);
            } finally {
                if (conexao != null) conexao.disconnect();
            }
        });
    }
    private void atualizarPreviewAvatar() {
        String nome = (String) CbAvataresServidor.getSelectedItem();
        if (nome == null || nome.isBlank()) {
            LblPreviewAvatar.setIcon(null);
            LblPreviewAvatar.setText("Sem avatar");
            return;
        }

        long idRequisicao = requisicaoPreviewAvatar.incrementAndGet();
        LblPreviewAvatar.setIcon(null);
        LblPreviewAvatar.setText("Carregando...");

        Thread.ofVirtual().start(() -> {
            java.net.HttpURLConnection conexao = null;
            try {
                String nomeCodificado = java.net.URLEncoder.encode(
                        nome, java.nio.charset.StandardCharsets.UTF_8
                ).replace("+", "%20");
                String endereco = Util.URL_FIGURINHAS + "avatars/" + nomeCodificado;

                conexao = (java.net.HttpURLConnection)
                        new java.net.URL(endereco).openConnection();
                conexao.setConnectTimeout(3000);
                conexao.setReadTimeout(5000);

                java.awt.image.BufferedImage original;
                try (var entrada = conexao.getInputStream()) {
                    original = javax.imageio.ImageIO.read(entrada);
                }
                if (original == null) throw new java.io.IOException("Formato de imagem não reconhecido.");

                java.awt.Image redimensionada = original.getScaledInstance(
                        64, 64, java.awt.Image.SCALE_SMOOTH
                );
                javax.swing.ImageIcon icone = new javax.swing.ImageIcon(redimensionada);

                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (requisicaoPreviewAvatar.get() == idRequisicao) {
                        LblPreviewAvatar.setIcon(icone);
                        LblPreviewAvatar.setText("");
                    }
                });
            } catch (Exception erro) {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (requisicaoPreviewAvatar.get() == idRequisicao) {
                        LblPreviewAvatar.setIcon(null);
                        LblPreviewAvatar.setText("Prévia indisponível");
                    }
                });
            } finally {
                if (conexao != null) conexao.disconnect();
            }
        });
    }
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        GrpCor = new javax.swing.ButtonGroup();
        GrpAvatar = new javax.swing.ButtonGroup();
        LblCor = new javax.swing.JLabel();
        TxtNick = new javax.swing.JTextField();
        LblNick = new javax.swing.JLabel();
        RadAzul = new javax.swing.JRadioButton();
        RadVerde = new javax.swing.JRadioButton();
        RadVermelho = new javax.swing.JRadioButton();
        LblAvatar = new javax.swing.JLabel();
        RadAvatar1 = new javax.swing.JRadioButton();
        LblAvatar1 = new javax.swing.JLabel();
        RadAvatar2 = new javax.swing.JRadioButton();
        LblAvatar2 = new javax.swing.JLabel();
        RadAvatar3 = new javax.swing.JRadioButton();
        LblAvatar3 = new javax.swing.JLabel();
        BtnEntrar = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Login");
        setMinimumSize(new java.awt.Dimension(400, 300));
        setName("FrmLogin"); // NOI18N
        getContentPane().setLayout(null);

        LblCor.setFont(new java.awt.Font("Liberation Sans", 1, 18)); // NOI18N
        LblCor.setText("Cor:");
        getContentPane().add(LblCor);
        LblCor.setBounds(60, 50, 50, 40);

        TxtNick.setFont(new java.awt.Font("Liberation Sans", 0, 18)); // NOI18N
        getContentPane().add(TxtNick);
        TxtNick.setBounds(110, 10, 280, 30);

        LblNick.setFont(new java.awt.Font("Liberation Sans", 1, 18)); // NOI18N
        LblNick.setText("Nickname:");
        getContentPane().add(LblNick);
        LblNick.setBounds(10, 10, 130, 40);

        GrpCor.add(RadAzul);
        RadAzul.setFont(new java.awt.Font("Liberation Sans", 1, 15)); // NOI18N
        RadAzul.setForeground(new java.awt.Color(0, 0, 102));
        RadAzul.setSelected(true);
        RadAzul.setText("Azul");
        RadAzul.addActionListener(this::RadAzulActionPerformed);
        getContentPane().add(RadAzul);
        RadAzul.setBounds(110, 60, 60, 22);

        GrpCor.add(RadVerde);
        RadVerde.setFont(new java.awt.Font("Liberation Sans", 1, 15)); // NOI18N
        RadVerde.setForeground(new java.awt.Color(0, 153, 0));
        RadVerde.setText("Verde");
        RadVerde.addActionListener(this::RadVerdeActionPerformed);
        getContentPane().add(RadVerde);
        RadVerde.setBounds(170, 60, 70, 22);

        GrpCor.add(RadVermelho);
        RadVermelho.setFont(new java.awt.Font("Liberation Sans", 1, 15)); // NOI18N
        RadVermelho.setForeground(new java.awt.Color(255, 0, 0));
        RadVermelho.setText("Vermelho");
        RadVermelho.addActionListener(this::RadVermelhoActionPerformed);
        getContentPane().add(RadVermelho);
        RadVermelho.setBounds(240, 60, 100, 22);

        LblAvatar.setFont(new java.awt.Font("Liberation Sans", 1, 18)); // NOI18N
        LblAvatar.setText("Avatar:");
        getContentPane().add(LblAvatar);
        LblAvatar.setBounds(40, 90, 70, 22);

        GrpAvatar.add(RadAvatar1);
        RadAvatar1.setSelected(true);
        RadAvatar1.addActionListener(this::RadAvatar1ActionPerformed);
        getContentPane().add(RadAvatar1);
        RadAvatar1.setBounds(110, 90, 19, 19);

        LblAvatar1.setIcon(new javax.swing.ImageIcon("/home/joao/Projetos/Sistemas-Distribuidos/SD-v4/Chat_Cliente_Desktop/src/main/java/images/icons8-lanterna-verde-16.png")); // NOI18N
        LblAvatar1.setLabelFor(RadAvatar1);
        getContentPane().add(LblAvatar1);
        LblAvatar1.setBounds(130, 90, 20, 20);

        GrpAvatar.add(RadAvatar2);
        RadAvatar2.addActionListener(this::RadAvatar2ActionPerformed);
        getContentPane().add(RadAvatar2);
        RadAvatar2.setBounds(160, 90, 19, 19);

        LblAvatar2.setIcon(new javax.swing.ImageIcon("/home/joao/Projetos/Sistemas-Distribuidos/SD-v4/Chat_Cliente_Desktop/src/main/java/images/icons8-the-flash-sign-16.png")); // NOI18N
        LblAvatar2.setLabelFor(RadAvatar2);
        getContentPane().add(LblAvatar2);
        LblAvatar2.setBounds(180, 90, 20, 20);

        GrpAvatar.add(RadAvatar3);
        RadAvatar3.addActionListener(this::RadAvatar3ActionPerformed);
        getContentPane().add(RadAvatar3);
        RadAvatar3.setBounds(210, 90, 19, 19);

        LblAvatar3.setIcon(new javax.swing.ImageIcon("/home/joao/Projetos/Sistemas-Distribuidos/SD-v4/Chat_Cliente_Desktop/src/main/java/images/icons8-batman-antigo-16.png")); // NOI18N
        LblAvatar3.setLabelFor(RadAvatar3);
        getContentPane().add(LblAvatar3);
        LblAvatar3.setBounds(230, 90, 20, 20);

        BtnEntrar.setFont(new java.awt.Font("Liberation Sans", 1, 18)); // NOI18N
        BtnEntrar.setText("ENTRAR");
        BtnEntrar.addActionListener(this::BtnEntrarActionPerformed);
        getContentPane().add(BtnEntrar);
        BtnEntrar.setBounds(110, 140, 170, 50);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void RadAzulActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_RadAzulActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_RadAzulActionPerformed

    private void RadVerdeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_RadVerdeActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_RadVerdeActionPerformed

    private void RadVermelhoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_RadVermelhoActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_RadVermelhoActionPerformed

    private void RadAvatar1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_RadAvatar1ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_RadAvatar1ActionPerformed

    private void RadAvatar2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_RadAvatar2ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_RadAvatar2ActionPerformed

    private void RadAvatar3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_RadAvatar3ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_RadAvatar3ActionPerformed

    private void BtnEntrarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BtnEntrarActionPerformed
        if (!TxtNick.getText().equals("")){
            Util.nickname = TxtNick.getText();
            if (RadAzul.isSelected()) {
                Util.cor = "Blue";

            } else if (RadVerde.isSelected()) {
                Util.cor = "MediumSeaGreen";

            } else if (RadVermelho.isSelected()) {
                Util.cor = "Tomato";
            }
            String avatarSelecionado = (String) CbAvataresServidor.getSelectedItem();

            if (avatarSelecionado == null
                    || avatarSelecionado.isBlank()) {

                avatarSelecionado = "menino.png";
            }

            Util.avatar = "avatars/" + avatarSelecionado;
            FrmChat frmchat = new FrmChat();
            frmchat.setVisible(true);
            this.dispose();
        } else {
            JOptionPane.showMessageDialog(null,"Digite um Nickname!");
        }
    }//GEN-LAST:event_BtnEntrarActionPerformed

    public static void main(String args[]) {
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Dark Metal".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(() -> new FrmLogin().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton BtnEntrar;
    private javax.swing.ButtonGroup GrpAvatar;
    private javax.swing.ButtonGroup GrpCor;
    private javax.swing.JLabel LblAvatar;
    private javax.swing.JLabel LblAvatar1;
    private javax.swing.JLabel LblAvatar2;
    private javax.swing.JLabel LblAvatar3;
    private javax.swing.JLabel LblCor;
    private javax.swing.JLabel LblNick;
    private javax.swing.JRadioButton RadAvatar1;
    private javax.swing.JRadioButton RadAvatar2;
    private javax.swing.JRadioButton RadAvatar3;
    private javax.swing.JRadioButton RadAzul;
    private javax.swing.JRadioButton RadVerde;
    private javax.swing.JRadioButton RadVermelho;
    private javax.swing.JTextField TxtNick;
    // End of variables declaration//GEN-END:variables
}
