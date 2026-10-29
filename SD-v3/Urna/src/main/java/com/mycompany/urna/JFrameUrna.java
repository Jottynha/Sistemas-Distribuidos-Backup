package com.mycompany.urna;

import java.io.ObjectOutputStream;
import java.net.Socket;
import javax.swing.JOptionPane;

public class JFrameUrna extends javax.swing.JFrame {
    int voto; // 1-4 candidatos, 5-Branco e 6-Nulo
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(JFrameUrna.class.getName());

    public JFrameUrna() {
        initComponents();
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">                          
    private void initComponents() {

        ButtonGroup = new javax.swing.ButtonGroup();
        LabelSelectCandidato = new javax.swing.JLabel();
        RadioButtonCandidato1 = new javax.swing.JRadioButton();
        RadioButtonCandidato2 = new javax.swing.JRadioButton();
        RadioButtonBranco = new javax.swing.JRadioButton();
        RadioButtonCandidato4 = new javax.swing.JRadioButton();
        RadioButtonCandidato3 = new javax.swing.JRadioButton();
        RadioButtonNulo = new javax.swing.JRadioButton();
        ButtonVote = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Urna Eletrônica");

        LabelSelectCandidato.setText("Selecione o Candidato:");

        ButtonGroup.add(RadioButtonCandidato1);
        RadioButtonCandidato1.setText("Candidato 1");
        RadioButtonCandidato1.addActionListener(this::RadioButtonCandidato1ActionPerformed);

        ButtonGroup.add(RadioButtonCandidato2);
        RadioButtonCandidato2.setText("Candidato 2");
        RadioButtonCandidato2.addActionListener(this::RadioButtonCandidato2ActionPerformed);

        ButtonGroup.add(RadioButtonBranco);
        RadioButtonBranco.setText("Branco");
        RadioButtonBranco.setToolTipText("");
        RadioButtonBranco.addActionListener(this::RadioButtonBrancoActionPerformed);

        ButtonGroup.add(RadioButtonCandidato4);
        RadioButtonCandidato4.setText("Candidato 4");
        RadioButtonCandidato4.addActionListener(this::RadioButtonCandidato4ActionPerformed);

        ButtonGroup.add(RadioButtonCandidato3);
        RadioButtonCandidato3.setText("Candidato 3");
        RadioButtonCandidato3.addActionListener(this::RadioButtonCandidato3ActionPerformed);

        ButtonGroup.add(RadioButtonNulo);
        RadioButtonNulo.setText("Nulo");
        RadioButtonNulo.addActionListener(this::RadioButtonNuloActionPerformed);

        ButtonVote.setText("Votar e Aguardar Apuração");
        ButtonVote.addActionListener(this::ButtonVoteActionPerformed);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(ButtonVote, javax.swing.GroupLayout.PREFERRED_SIZE, 231, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(LabelSelectCandidato)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(RadioButtonCandidato1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(RadioButtonCandidato3))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(RadioButtonCandidato2)
                            .addComponent(RadioButtonBranco))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(RadioButtonNulo)
                            .addComponent(RadioButtonCandidato4))))
                .addContainerGap(136, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(LabelSelectCandidato)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(RadioButtonCandidato1)
                    .addComponent(RadioButtonCandidato3))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(RadioButtonCandidato2)
                    .addComponent(RadioButtonCandidato4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(RadioButtonBranco)
                    .addComponent(RadioButtonNulo))
                .addGap(18, 18, 18)
                .addComponent(ButtonVote, javax.swing.GroupLayout.PREFERRED_SIZE, 53, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(54, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>                        

    private void RadioButtonCandidato1ActionPerformed(java.awt.event.ActionEvent evt) {                                                      
        voto = 1;
    }                                                     

    private void RadioButtonCandidato2ActionPerformed(java.awt.event.ActionEvent evt) {                                                      
        voto = 2;
    }                                                     

    private void RadioButtonBrancoActionPerformed(java.awt.event.ActionEvent evt) {                                                  
        voto = 5;
    }                                                 

    private void RadioButtonCandidato4ActionPerformed(java.awt.event.ActionEvent evt) {                                                      
        voto = 4;
    }                                                     

    private void RadioButtonCandidato3ActionPerformed(java.awt.event.ActionEvent evt) {                                                      
        voto = 3;
    }                                                     

    private void RadioButtonNuloActionPerformed(java.awt.event.ActionEvent evt) {                                                
        voto = 6;
    }                                               

    private void ButtonVoteActionPerformed(java.awt.event.ActionEvent evt) {                                           
        if(RadioButtonCandidato1.isSelected() || RadioButtonCandidato2.isSelected() ||
           RadioButtonCandidato3.isSelected() || RadioButtonCandidato4.isSelected() ||
           RadioButtonBranco.isSelected() || RadioButtonNulo.isSelected()){
            try{
                Socket client = new Socket("127.0.0.1", 3322);
                ObjectOutputStream writer = new ObjectOutputStream(client.getOutputStream());
                writer.writeInt(voto);
                writer.flush();
                writer.close();
                client.close();
                //JOptionPane.showMessageDialog(this, "Voto registrado com sucesso!");
            } catch (Exception error) {
                JOptionPane.showMessageDialog(this, "Erro ao conectar com a urna central:\n" + error);
                return; 
            }
            this.dispose();
            new JFrameApuração().setVisible(true);
        } else {
            JOptionPane.showMessageDialog(this, "Por favor, selecione uma opção!");
        }
    }                                          

    public static void main(String args[]) {
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }
        java.awt.EventQueue.invokeLater(() -> new JFrameUrna().setVisible(true));
    }

    // Variables declaration - do not modify                     
    private javax.swing.ButtonGroup ButtonGroup;
    private javax.swing.JButton ButtonVote;
    private javax.swing.JLabel LabelSelectCandidato;
    private javax.swing.JRadioButton RadioButtonBranco;
    private javax.swing.JRadioButton RadioButtonCandidato1;
    private javax.swing.JRadioButton RadioButtonCandidato2;
    private javax.swing.JRadioButton RadioButtonCandidato3;
    private javax.swing.JRadioButton RadioButtonCandidato4;
    private javax.swing.JRadioButton RadioButtonNulo;
    // End of variables declaration                   
}