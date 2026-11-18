package com.mycompany.exemplo_bancodedados;

import static java.lang.System.out;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.swing.JOptionPane;

public class Exemplo_BancodeDados {

    public static void main(String[] args) {
        //conecta ao servidor de banco de dados
        try {
        Connection conexao = DriverManager.getConnection("jdbc:mysql://10.112.75.143:3307/teste","root","root"); //ip/porta/bd/usuario/senha
        //executa comando sql
        //Statement stm = conexao.createStatement();
        String nome = JOptionPane.showInputDialog(null,"Escreva o nome desejado para inserir:");
        String telefone = JOptionPane.showInputDialog(null,"Escreva o telefone desejado para inserir:");
        //stm.execute("insert into clientes(nome,telefone) values ('joao-pedro','303031')");
        PreparedStatement pstm = conexao.prepareStatement("insert into clientes(nome,telefone) values (?, ?)");
        pstm.setString(1, nome);
        pstm.setString(2, telefone);
        ResultSet rs = pstm.executeUpdate();
        //buscando dados no servidor
        PreparedStatement pstm2 = conexao.prepareStatement("select nome,telefone from clientes where id>? and id<?");
        pstm2.setInt(1, 1);
        pstm2.setInt(2, 100);
        rs = pstm2.executeQuery();
        while(rs.next()){
            out.print("\n\nNome: " + rs.getString("Nome") + "\nTelefone: " + rs.getString("Telefone"));
        }
        String nome2 = JOptionPane.showInputDialog(null,"Escreva o nome desejado para deletar:");
        PreparedStatement pstm3 = conexao.prepareStatement("DELETE FROM clientes WHERE nome = ?");
        pstm.setString(1, nome2);
        PreparedStatement pstm4 = conexao.prepareStatement("select nome,telefone from clientes where id>? and id<?");
        pstm4.setInt(1, 1);
        pstm4.setInt(2, 100);
        rs = pstm4.executeQuery();
        while(rs.next()){
            out.print("\n\nNome: " + rs.getString("Nome") + "\nTelefone: " + rs.getString("Telefone"));
        }
        rs.close();
        pstm.close();
        pstm2.close();
        pstm3.close();
        conexao.close();
        } catch(Exception e){
            out.print(e.getMessage());
        }
    }
}
