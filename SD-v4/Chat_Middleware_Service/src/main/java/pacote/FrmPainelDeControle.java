package pacote;
import javax.swing.JOptionPane;

public class FrmPainelDeControle extends javax.swing.JFrame {
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(FrmPainelDeControle.class.getName());
    public FrmPainelDeControle() {
        initComponents();
    }
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        LBLSrvDesktop = new javax.swing.JLabel();
        LBLSrvDesktopLegenda = new javax.swing.JLabel();
        BtnAtivarDesktop = new javax.swing.JToggleButton();
        LBLSrvWeb = new javax.swing.JLabel();
        LBLSrvWebLegenda = new javax.swing.JLabel();
        BtnAtivarWeb = new javax.swing.JToggleButton();
        LBLSrvTerceiros = new javax.swing.JLabel();
        LBLSrvTerceirosLegenda = new javax.swing.JLabel();
        BtnAtivarTerceiros = new javax.swing.JToggleButton();
        LBLSrvPublicidade = new javax.swing.JLabel();
        LBLSrvPublicidadeLegenda = new javax.swing.JLabel();
        BtnAtivarPublicidade = new javax.swing.JToggleButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Painel de Controle");

        LBLSrvDesktop.setFont(new java.awt.Font("Liberation Sans", 0, 12)); // NOI18N
        LBLSrvDesktop.setForeground(new java.awt.Color(255, 0, 0));
        LBLSrvDesktop.setText("Inspirado no Whatsapp Desktop");

        LBLSrvDesktopLegenda.setFont(new java.awt.Font("Liberation Sans", 0, 20)); // NOI18N
        LBLSrvDesktopLegenda.setText("Serviço Desktop:");

        BtnAtivarDesktop.setFont(new java.awt.Font("Liberation Sans", 0, 25)); // NOI18N
        BtnAtivarDesktop.setText("Ativar");
        BtnAtivarDesktop.addActionListener(this::BtnAtivarDesktopActionPerformed);

        LBLSrvWeb.setFont(new java.awt.Font("Liberation Sans", 0, 12)); // NOI18N
        LBLSrvWeb.setForeground(new java.awt.Color(255, 0, 0));
        LBLSrvWeb.setText("Inspirado no Whatsapp Web");

        LBLSrvWebLegenda.setFont(new java.awt.Font("Liberation Sans", 0, 20)); // NOI18N
        LBLSrvWebLegenda.setText("Serviço Web:");

        BtnAtivarWeb.setFont(new java.awt.Font("Liberation Sans", 0, 25)); // NOI18N
        BtnAtivarWeb.setText("Ativar");
        BtnAtivarWeb.addActionListener(this::BtnAtivarWebActionPerformed);

        LBLSrvTerceiros.setFont(new java.awt.Font("Liberation Sans", 0, 12)); // NOI18N
        LBLSrvTerceiros.setForeground(new java.awt.Color(255, 0, 0));
        LBLSrvTerceiros.setText("Software de Outras Linguagens");

        LBLSrvTerceirosLegenda.setFont(new java.awt.Font("Liberation Sans", 0, 20)); // NOI18N
        LBLSrvTerceirosLegenda.setText("Serviço Terceiros:");

        BtnAtivarTerceiros.setFont(new java.awt.Font("Liberation Sans", 0, 25)); // NOI18N
        BtnAtivarTerceiros.setText("Ativar");
        BtnAtivarTerceiros.addActionListener(this::BtnAtivarTerceirosActionPerformed);

        LBLSrvPublicidade.setFont(new java.awt.Font("Liberation Sans", 0, 12)); // NOI18N
        LBLSrvPublicidade.setForeground(new java.awt.Color(255, 0, 0));
        LBLSrvPublicidade.setText("Envio de Propaganda em Multicast");

        LBLSrvPublicidadeLegenda.setFont(new java.awt.Font("Liberation Sans", 0, 20)); // NOI18N
        LBLSrvPublicidadeLegenda.setText("Serviço Publicidade:");

        BtnAtivarPublicidade.setFont(new java.awt.Font("Liberation Sans", 0, 25)); // NOI18N
        BtnAtivarPublicidade.setText("Ativar");
        BtnAtivarPublicidade.addActionListener(this::BtnAtivarPublicidadeActionPerformed);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(LBLSrvTerceiros)
                    .addComponent(LBLSrvWeb)
                    .addComponent(LBLSrvDesktop)
                    .addComponent(LBLSrvWebLegenda)
                    .addComponent(LBLSrvDesktopLegenda)
                    .addComponent(LBLSrvTerceirosLegenda)
                    .addComponent(LBLSrvPublicidade)
                    .addComponent(LBLSrvPublicidadeLegenda))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(BtnAtivarPublicidade, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(BtnAtivarDesktop, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(BtnAtivarWeb, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(BtnAtivarTerceiros, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 192, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(7, 7, 7)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(LBLSrvDesktopLegenda)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(LBLSrvDesktop))
                    .addComponent(BtnAtivarDesktop, javax.swing.GroupLayout.PREFERRED_SIZE, 55, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(BtnAtivarWeb, javax.swing.GroupLayout.PREFERRED_SIZE, 55, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(LBLSrvWebLegenda)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(LBLSrvWeb)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(BtnAtivarTerceiros, javax.swing.GroupLayout.PREFERRED_SIZE, 55, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(LBLSrvTerceirosLegenda)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(LBLSrvTerceiros)))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(BtnAtivarPublicidade, javax.swing.GroupLayout.PREFERRED_SIZE, 55, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(LBLSrvPublicidadeLegenda)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(LBLSrvPublicidade)))
                .addContainerGap(25, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void BtnAtivarDesktopActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BtnAtivarDesktopActionPerformed

        if (BtnAtivarDesktop.isSelected()) {
            BtnAtivarDesktop.setText("Desativar");
            Util.desktopRecepcaoThread = new DesktopRecepcaoThread();
            Thread.ofVirtual().start(Util.desktopRecepcaoThread);
            Util.desktopEnvioThread = new DesktopEnvioThread();
            Thread.ofVirtual().start(Util.desktopEnvioThread);
        } else {
            int resposta = JOptionPane.showConfirmDialog(null,"Deseja realmente parar os Serviços para clientes DESKTOP?","Confirmação",JOptionPane.YES_NO_OPTION);
            if (resposta == JOptionPane.YES_OPTION) {
                if (Util.desktopRecepcaoThread != null) {
                    Util.desktopRecepcaoThread.fecharServidor();
                }
                if (Util.desktopEnvioThread != null) {
                    Util.desktopEnvioThread.fecharServidor();
                }
                Util.desktopRecepcaoThread = null;
                Util.desktopEnvioThread = null;
                BtnAtivarDesktop.setText("Ativar");
            } else {
                BtnAtivarDesktop.setSelected(true);
            }
        }
    }//GEN-LAST:event_BtnAtivarDesktopActionPerformed

    private void BtnAtivarWebActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BtnAtivarWebActionPerformed
        if(BtnAtivarWeb.isSelected()==true){
            BtnAtivarWeb.setText("Desativar");
            //...
        } else {
            BtnAtivarWeb.setText("Ativar");
        }
    }//GEN-LAST:event_BtnAtivarWebActionPerformed

    private void BtnAtivarTerceirosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BtnAtivarTerceirosActionPerformed
        if(BtnAtivarTerceiros.isSelected()==true){
            BtnAtivarTerceiros.setText("Desativar");
            //...
        } else {
            BtnAtivarTerceiros.setText("Ativar");
        }
    }//GEN-LAST:event_BtnAtivarTerceirosActionPerformed

    private void BtnAtivarPublicidadeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BtnAtivarPublicidadeActionPerformed
        if(BtnAtivarPublicidade.isSelected()==true){
            BtnAtivarPublicidade.setText("Desativar");
            //...
        } else {
            BtnAtivarPublicidade.setText("Ativar");
        }
    }//GEN-LAST:event_BtnAtivarPublicidadeActionPerformed

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(() -> new FrmPainelDeControle().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JToggleButton BtnAtivarDesktop;
    private javax.swing.JToggleButton BtnAtivarPublicidade;
    private javax.swing.JToggleButton BtnAtivarTerceiros;
    private javax.swing.JToggleButton BtnAtivarWeb;
    private javax.swing.JLabel LBLSrvDesktop;
    private javax.swing.JLabel LBLSrvDesktopLegenda;
    private javax.swing.JLabel LBLSrvPublicidade;
    private javax.swing.JLabel LBLSrvPublicidadeLegenda;
    private javax.swing.JLabel LBLSrvTerceiros;
    private javax.swing.JLabel LBLSrvTerceirosLegenda;
    private javax.swing.JLabel LBLSrvWeb;
    private javax.swing.JLabel LBLSrvWebLegenda;
    // End of variables declaration//GEN-END:variables
}
