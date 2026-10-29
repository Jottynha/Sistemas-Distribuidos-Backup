

package com.mycompany.urna;

import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import javax.swing.JOptionPane;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Urna {
    public static void main(String[] args) {
        int Candidato1=0;
        int Candidato2=0;
        int Candidato3=0;
        for (int i = 1; i < 3; i++) {
            try {
                ServerSocket server = new ServerSocket(3322);
                Socket client = server.accept();
                JOptionPane.showMessageDialog(null,"Eleitor [" + i + "] conectado!");
                ObjectInputStream reader = new ObjectInputStream(client.getInputStream());
                int voto = reader.readInt();
                JOptionPane.showMessageDialog(null,"Voto ["+i+"]:\n" + voto);
                switch (voto){
                    case 1 -> Candidato1+=1;
                    case 2 -> Candidato2+=1;
                    case 3 -> Candidato3+=1;
                    default -> {
                    }
                }
                reader.close();
                byte[] msg = ("230.0.0.13").getBytes();
                InetAddress addr = InetAddress.getByName("200.128.142.91");
                DatagramPacket pckt = new DatagramPacket(msg,msg.length,addr,6666);
                DatagramSocket ds = new DatagramSocket();
                ds.send(pckt);
                System.out.println("Enviada a:" + addr.getHostAddress());
                client.close();
                server.close();
                } catch (Exception error) {
                    JOptionPane.showMessageDialog(null,"Erro no cliente:\n" + error);
                }
            if (i==2){
                try {
                    byte[] b = ("Votos computados:\n" + Candidato1 + "\n" + Candidato2 + "\n" + Candidato3 + "\n").getBytes();
                    InetAddress addr = InetAddress.getByName("230.0.0.13");
                    DatagramSocket ds = new DatagramSocket();
                    DatagramPacket pckt = new DatagramPacket(b,b.length,addr,6668);
                    ds.send(pckt);
                    System.out.println("Mensagem enviada!");
                    ds.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(null,"Erro no emissor: " + e.getMessage());
                }
            }
        }
    }
}
