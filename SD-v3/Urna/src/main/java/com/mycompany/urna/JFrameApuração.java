package com.mycompany.urna;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import javax.swing.SwingUtilities;
import javax.swing.SwingConstants;

public class JFrameApuração extends javax.swing.JFrame {

    private javax.swing.JLabel labelResultados;

    public JFrameApuração() {
        initComponentsCustom();
        iniciarRecebimentoApuracao();
    }

    private void initComponentsCustom() {
        labelResultados = new javax.swing.JLabel();
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Resultados");
        setPreferredSize(new java.awt.Dimension(400, 300));
        // Configurando a label com texto inicial
        labelResultados.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 18)); 
        labelResultados.setHorizontalAlignment(SwingConstants.CENTER);
        labelResultados.setText("<html><body><i>Aguardando fim da votação...</i></body></html>");
        getContentPane().setLayout(new java.awt.BorderLayout());
        getContentPane().add(labelResultados, java.awt.BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null); // Abre a janela no centro da tela
    }

    private void iniciarRecebimentoApuracao() {
        // Thread para não travar a interface gráfica
        new Thread(() -> {
            try {
                InetAddress group = InetAddress.getByName("230.0.0.13");
                MulticastSocket s = new MulticastSocket(6668);
                s.joinGroup(group);
                byte[] b = new byte[1024];
                DatagramPacket pckt = new DatagramPacket(b, b.length);
                s.receive(pckt); // A thread para aqui esperando o servidor enviar os dados
                String resultado = new String(pckt.getData(), 0, pckt.getLength());
                // Atualiza a Label com o HTML recebido
                SwingUtilities.invokeLater(() -> labelResultados.setText(resultado));
                s.leaveGroup(group);
                s.close();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> 
                    labelResultados.setText("Erro ao obter resultados:\n" + e.getMessage())
                );
            }
        }).start();
    }

    public static void main(String args[]) {
        try {
            for (javax.swing8.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(JFrameApuração.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }

        java.awt.EventQueue.invokeLater(() -> new JFrameApuração().setVisible(true));
    }
}