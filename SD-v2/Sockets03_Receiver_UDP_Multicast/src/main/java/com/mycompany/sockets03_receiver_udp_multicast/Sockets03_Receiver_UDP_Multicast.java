package com.mycompany.sockets03_receiver_udp_multicast;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import javax.swing.JOptionPane;

public class Sockets03_Receiver_UDP_Multicast {
    public static void main(String[] args) {
        try{
            InetAddress addr = InetAddress.getByName("239.0.0.10");
            InetSocketAddress group = new InetSocketAddress(addr,6668);
            NetworkInterface netIf = NetworkInterface.getByName("wlo1");
            MulticastSocket s = new MulticastSocket(group.getPort());
            s.joinGroup(group, netIf);
            byte[] b = new byte[256];
            DatagramPacket pckt = new DatagramPacket(b,b.length);
            s.receive(pckt);
            JOptionPane.showMessageDialog(null,new String(b).trim());
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,"Erro no emissor: " + e.getMessage());
        }
    }
}
