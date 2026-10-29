package com.mycompany.eleitor;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.Socket;
import javax.swing.JOptionPane;

public class Eleitor {

    public static void main(String[] args) {
        try{
            Socket client = new Socket("127.0.0.1", 3322);
            ObjectOutputStream writer = new ObjectOutputStream(client.getOutputStream());
            writer.flush();
            String msg = JOptionPane.showInputDialog("Voto: "); 
            int voto = Integer.parseInt(msg.trim());
            writer.writeInt(voto);
            writer.flush();
            DatagramSocket ds = new DatagramSocket(6666);
            byte[] ip = new byte[256];
            DatagramPacket pckt = new DatagramPacket(ip, ip.length);
            ds.receive(pckt);
            ds.close();
            String multicast = new String(pckt.getData()).trim();
            InetAddress addr = InetAddress.getByName(multicast);
            InetSocketAddress group = new InetSocketAddress(addr,6668);
            NetworkInterface netIf = NetworkInterface.getByName("wlo1");
            MulticastSocket s = new MulticastSocket(group.getPort());
            s.joinGroup(group, netIf);
            byte[] b = new byte[1024];
            DatagramPacket pckt2 = new DatagramPacket(b,b.length);
            s.receive(pckt2);
            JOptionPane.showMessageDialog(null,new String(b).trim());
        } catch (Exception error) {
            JOptionPane.showMessageDialog(null,"Erro no cliente:\n" + error);
        }
    }
}
