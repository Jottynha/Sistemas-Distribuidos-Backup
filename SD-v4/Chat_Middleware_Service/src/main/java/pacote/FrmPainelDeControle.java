package pacote;
import javax.swing.JOptionPane;
import java.io.IOException;

public class FrmPainelDeControle extends javax.swing.JFrame {
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(FrmPainelDeControle.class.getName());
    public FrmPainelDeControle() {

        initComponents();

        // Ao fechar o painel, encerra o middleware.
        setDefaultCloseOperation(
                javax.swing.WindowConstants.EXIT_ON_CLOSE
        );
        LBLSrvPublicidadeLegenda.setText(
                "Servico adicional:"
        );

        LBLSrvPublicidade.setText(
                "Nao utilizado nesta atividade"
        );
        

        BtnAtivarTerceiros.setEnabled(false);
        BtnAtivarTerceiros.setText("N/A");

        BtnAtivarPublicidade.setEnabled(false);
        BtnAtivarPublicidade.setText("N/A");
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
        BtnAtivarLog = new javax.swing.JToggleButton();
        LBLLog = new javax.swing.JLabel();
        LBLLogLegenda = new javax.swing.JLabel();

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

        BtnAtivarLog.setFont(new java.awt.Font("Segoe UI", 0, 25)); // NOI18N
        BtnAtivarLog.setText("Ativar");
        BtnAtivarLog.addActionListener(this::BtnAtivarLogActionPerformed);

        LBLLog.setFont(new java.awt.Font("Liberation Sans", 0, 20)); // NOI18N
        LBLLog.setText("Serviço Log");

        LBLLogLegenda.setFont(new java.awt.Font("Liberation Sans", 0, 12)); // NOI18N
        LBLLogLegenda.setForeground(new java.awt.Color(255, 0, 0));
        LBLLogLegenda.setText("Ativar rastreamento de Log");

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
                    .addComponent(LBLSrvPublicidadeLegenda)
                    .addComponent(LBLLog)
                    .addComponent(LBLLogLegenda))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(BtnAtivarLog, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
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
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(BtnAtivarLog, javax.swing.GroupLayout.PREFERRED_SIZE, 53, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(LBLLog)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(LBLLogLegenda)))
                .addContainerGap(54, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void BtnAtivarDesktopActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BtnAtivarDesktopActionPerformed


        if (BtnAtivarDesktop.isSelected()) {

            try {

                Util.iniciarDesktop();

                BtnAtivarDesktop.setText("Desativar");

                LBLSrvDesktop.setText(
                        "Servidor Desktop ativado"
                );

                LBLSrvDesktop.setForeground(
                        new java.awt.Color(0, 150, 0)
                );

            } catch (Exception e) {

                BtnAtivarDesktop.setSelected(false);
                BtnAtivarDesktop.setText("Ativar");

                Util.registrarErroServidor(
                        "internal",
                        "INTERNO/NÃO APLICÁVEL",
                        "NÃO APLICÁVEL",
                        "FrmPainelDeControle.BtnAtivarDesktop",
                        e
                );

                JOptionPane.showMessageDialog(
                        this,
                        "Nao foi possivel iniciar o servico Desktop:\n"
                        + e.getMessage(),
                        "Erro",
                        JOptionPane.ERROR_MESSAGE
                );
            }

        } else {

            int resposta = JOptionPane.showConfirmDialog(
                    this,
                    "Deseja realmente parar o servico Desktop?",
                    "Confirmacao",
                    JOptionPane.YES_NO_OPTION
            );

            if (resposta == JOptionPane.YES_OPTION) {

                Util.pararDesktop();

                BtnAtivarDesktop.setText("Ativar");

                LBLSrvDesktop.setText(
                        "Inspirado no Whatsapp Desktop"
                );

                LBLSrvDesktop.setForeground(
                        new java.awt.Color(255, 0, 0)
                );

            } else {

                BtnAtivarDesktop.setSelected(true);
            }
        }
    }//GEN-LAST:event_BtnAtivarDesktopActionPerformed

    private void BtnAtivarWebActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BtnAtivarWebActionPerformed

    if (BtnAtivarWeb.isSelected()) {

        try {

            Util.iniciarServidorWeb();

            BtnAtivarWeb.setText("Desativar");

            LBLSrvWeb.setText(
                    "Servidor Web ativado - Porta 8081"
            );

            LBLSrvWeb.setForeground(
                    new java.awt.Color(0, 150, 0)
            );

        } catch (Exception e) {

            BtnAtivarWeb.setSelected(false);
            BtnAtivarWeb.setText("Ativar");

            Util.registrarErroServidor(
                    "internal",
                    "INTERNO/NÃO APLICÁVEL",
                    "NÃO APLICÁVEL",
                    "FrmPainelDeControle.BtnAtivarWeb",
                    e
            );

            JOptionPane.showMessageDialog(
                    this,
                    "Nao foi possivel iniciar o servico Web:\n"
                    + e.getMessage(),
                    "Erro",
                    JOptionPane.ERROR_MESSAGE
            );
        }

    } else {

        int resposta = JOptionPane.showConfirmDialog(
                this,
                "Deseja realmente parar o servico Web?",
                "Confirmacao",
                JOptionPane.YES_NO_OPTION
        );

        if (resposta == JOptionPane.YES_OPTION) {

            Util.pararServidorWeb();

            BtnAtivarWeb.setText("Ativar");

            LBLSrvWeb.setText(
                    "Inspirado no Whatsapp Web"
            );

            LBLSrvWeb.setForeground(
                    new java.awt.Color(255, 0, 0)
            );

        } else {

            BtnAtivarWeb.setSelected(true);
        }
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

    private void BtnAtivarLogActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BtnAtivarLogActionPerformed
   if (BtnAtivarLog.isSelected()) {

        try {

            Util.iniciarColetorLogs();

            BtnAtivarLog.setText("Desativar");

            LBLLog.setText(
                    "Coletor ativo - Porta 8082"
            );

            LBLLog.setForeground(
                    new java.awt.Color(0, 150, 0)
            );

        } catch (Exception e) {

            BtnAtivarLog.setSelected(false);
            BtnAtivarLog.setText("Ativar");

            Util.registrarErroServidor(
                    "internal",
                    "INTERNO/NÃO APLICÁVEL",
                    "NÃO APLICÁVEL",
                    "FrmPainelDeControle.BtnAtivarLog",
                    e
            );

            JOptionPane.showMessageDialog(
                    this,
                    "Nao foi possivel iniciar o coletor de logs:\n"
                    + e.getMessage(),
                    "Erro",
                    JOptionPane.ERROR_MESSAGE
            );
        }

    } else {

        int resposta = JOptionPane.showConfirmDialog(
                this,
                "Deseja realmente parar o coletor de logs?",
                "Confirmacao",
                JOptionPane.YES_NO_OPTION
        );

        if (resposta == JOptionPane.YES_OPTION) {

            Util.pararColetorLogs();

            BtnAtivarLog.setText("Ativar");

            LBLLog.setText(
                    "Erros dos clientes Desktop, Web e outros"
            );

            LBLLog.setForeground(
                    new java.awt.Color(255, 0, 0)
            );

        } else {

            BtnAtivarLog.setSelected(true);
        }
    }
    }//GEN-LAST:event_BtnAtivarLogActionPerformed

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(() -> new FrmPainelDeControle().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JToggleButton BtnAtivarDesktop;
    private javax.swing.JToggleButton BtnAtivarLog;
    private javax.swing.JToggleButton BtnAtivarPublicidade;
    private javax.swing.JToggleButton BtnAtivarTerceiros;
    private javax.swing.JToggleButton BtnAtivarWeb;
    private javax.swing.JLabel LBLLog;
    private javax.swing.JLabel LBLLogLegenda;
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
