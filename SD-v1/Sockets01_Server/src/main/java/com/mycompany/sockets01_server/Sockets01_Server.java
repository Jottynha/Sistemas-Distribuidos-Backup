/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.mycompany.sockets01_server;

import javax.swing.JOptionPane;

/**
 *
 * @author joao
 */
public class Sockets01_Server {

    public static void main(String[] args) {
        ServidorTCP servidor = new ServidorTCP();
        for(int i=1;i<3;i++){ //Buscando dois clientes
            servidor.execute(i);
        }
        JOptionPane.showMessageDialog(null,servidor.logConversa);
    }
}
