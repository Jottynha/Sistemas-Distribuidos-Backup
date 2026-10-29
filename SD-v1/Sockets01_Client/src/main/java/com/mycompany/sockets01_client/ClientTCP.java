package com.mycompany.sockets01_client;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import javax.swing.JOptionPane;

public class ClientTCP {
    public void execute() {
        try {
            Socket client = new Socket("200.128.141.225", 3322);
            ObjectInputStream reader = new ObjectInputStream(client.getInputStream());
            ObjectOutputStream writer = new ObjectOutputStream(client.getOutputStream());
            writer.flush();
            String mensagem = reader.readUTF();
            JOptionPane.showMessageDialog(null,"Mensagem recebida:\n" + mensagem);
            String resposta = JOptionPane.showInputDialog(null,"Digite sua resposta:");
            writer.writeUTF(resposta);
            writer.flush();
            reader.close();
            writer.close();
            client.close();
        } catch (Exception error) {
            JOptionPane.showMessageDialog(null,"Erro no cliente:\n" + error);
        }
    }
}