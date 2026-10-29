package com.mycompany.urna;

import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class ServidorUrna {
    public static void main(String[] args) {
        int candidato1 = 0, candidato2 = 0, candidato3 = 0, candidato4 = 0, branco = 0, nulo = 0;
        int totalEleitores = 3; 
        try (ServerSocket server = new ServerSocket(3322)) {
            System.out.println("Servidor iniciado. Aguardando " + totalEleitores + " votos...");
            for (int i = 1; i <= totalEleitores; i++) {
                Socket client = server.accept();
                ObjectInputStream reader = new ObjectInputStream(client.getInputStream());
                int voto = reader.readInt();
                switch (voto) {
                    case 1 -> candidato1++;
                    case 2 -> candidato2++;
                    case 3 -> candidato3++;
                    case 4 -> candidato4++;
                    case 5 -> branco++;
                    case 6 -> nulo++;
                }
                reader.close();
                client.close();
                System.out.println("Voto " + i + " computado com sucesso.");
            }
            double pctC1 = (candidato1 * 100.0) / totalEleitores;
            double pctC2 = (candidato2 * 100.0) / totalEleitores;
            double pctC3 = (candidato3 * 100.0) / totalEleitores;
            double pctC4 = (candidato4 * 100.0) / totalEleitores;
            double pctBranco = (branco * 100.0) / totalEleitores;
            double pctNulo = (nulo * 100.0) / totalEleitores;
            // Formata o texto com HTML usando String.format para exibir 1 casa decimal (%.1f)
            String resultado = String.format(
                "<html><body><h2 style='text-align: center;'>=== RESULTADO OFICIAL ===</h2>"
                + "<b>Candidato 1:</b> %d (%.1f%%)<br>"
                + "<b>Candidato 2:</b> %d (%.1f%%)<br>"
                + "<b>Candidato 3:</b> %d (%.1f%%)<br>"
                + "<b>Candidato 4:</b> %d (%.1f%%)<br>"
                + "<b>Branco:</b> %d (%.1f%%)<br>"
                + "<b>Nulo:</b> %d (%.1f%%)</body></html>",
                candidato1, pctC1, 
                candidato2, pctC2, 
                candidato3, pctC3, 
                candidato4, pctC4, 
                branco, pctBranco, 
                nulo, pctNulo
            );
            // Realiza envio Multicast
            InetAddress group = InetAddress.getByName("230.0.0.13");
            DatagramSocket ds = new DatagramSocket();
            byte[] b = resultado.getBytes();
            DatagramPacket pckt = new DatagramPacket(b, b.length, group, 6668);
            ds.send(pckt);
            System.out.println("Resultados disparados via Multicast para todas as apurações!");
            ds.close();
        } catch (Exception e) {
            System.err.println("Erro no Servidor: " + e.getMessage());
        }
    }
}