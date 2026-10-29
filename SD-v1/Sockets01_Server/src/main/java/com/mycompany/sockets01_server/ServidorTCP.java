package com.mycompany.sockets01_server;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import javax.swing.JOptionPane;

public class ServidorTCP {
    String logConversa = "";
    public void execute(int i) {
        try {
            ServerSocket server = new ServerSocket(3322);
            Socket client = server.accept();
            JOptionPane.showMessageDialog(null,"Cliente [" + i + "] conectado!");
            ObjectOutputStream writer = new ObjectOutputStream(client.getOutputStream());
            writer.flush();
            ObjectInputStream reader = new ObjectInputStream(client.getInputStream());
            String msg = JOptionPane.showInputDialog(null,"Mensagem para Cliente [" + i + "]:");
            logConversa += "MSG ["+ i +"] SRV -> CLT (" + client.getInetAddress().getHostAddress() + "): " + msg + "\n";
            writer.writeUTF(msg);
            writer.flush();
            String resposta = reader.readUTF();
            JOptionPane.showMessageDialog(null,"Resposta do Cliente ["+i+"]:\n" + resposta);
            logConversa += "MSG ["+ i +"]  CLT (" + client.getInetAddress().getHostAddress() + ") -> SRV: " + resposta + "\n";
            reader.close();
            writer.close();
            client.close();
            server.close();
        } catch (Exception error) {
            JOptionPane.showMessageDialog(null,"Erro no servidor:\n" + error);
        }
    }
}