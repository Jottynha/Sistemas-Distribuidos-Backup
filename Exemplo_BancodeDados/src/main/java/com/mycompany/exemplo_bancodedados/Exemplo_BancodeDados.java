package com.mycompany.exemplo_bancodedados;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

public class Exemplo_BancodeDados extends JFrame {
    private static final String URL = "jdbc:mysql://10.210.215.143:3306/teste";
    private static final String USER = "root";
    private static final String PASS = "root";
    private JTextField txtNome;
    private JTextField txtTelefone;
    private DefaultTableModel modeloTabela;
    private JTable tabela;
    public Exemplo_BancodeDados() {
        // Janela
        setTitle("Exemplo Banco de Dados");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        // Formulário
        JPanel painelFormulario = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        painelFormulario.add(new JLabel("Nome:"));
        txtNome = new JTextField(12);
        painelFormulario.add(txtNome);
        painelFormulario.add(new JLabel("Telefone:"));
        txtTelefone = new JTextField(10);
        painelFormulario.add(txtTelefone);
        JButton btnAdicionar = new JButton("Adicionar");
        painelFormulario.add(btnAdicionar);
        add(painelFormulario, BorderLayout.NORTH);
        // Tabela
        modeloTabela = new DefaultTableModel(new Object[]{"ID", "Nome", "Telefone"}, 0);
        tabela = new JTable(modeloTabela);
        add(new JScrollPane(tabela), BorderLayout.CENTER);
        // Botão ADD
        btnAdicionar.addActionListener((ActionEvent e) -> {cadastrarCliente();});
        carregarTabela();
    }

    private void cadastrarCliente() {
        String nome = txtNome.getText().trim();
        String telefone = txtTelefone.getText().trim();
        if (nome.isEmpty() || telefone.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Preencha todos os campos!", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            try (Connection conexao = DriverManager.getConnection(URL, USER, PASS)) {
                PreparedStatement pstm = conexao.prepareStatement("INSERT INTO clientes(nome, telefone) VALUES (?, ?)");
                pstm.setString(1, nome);
                pstm.setString(2, telefone);
                pstm.executeUpdate();
                JOptionPane.showMessageDialog(this, "Cadastrado com sucesso!");
                txtNome.setText("");
                txtTelefone.setText("");
                carregarTabela();
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Erro ao cadastrar: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void carregarTabela() {
        modeloTabela.setRowCount(0);
        try {
            try (Connection conexao = DriverManager.getConnection(URL, USER, PASS)) {
                PreparedStatement pstm = conexao.prepareStatement("SELECT id, nome, telefone FROM clientes");
                ResultSet rs = pstm.executeQuery();
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String nome = rs.getString("nome");
                    String telefone = rs.getString("telefone");
                    modeloTabela.addRow(new Object[]{id, nome, telefone});
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Erro ao carregar dados: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new Exemplo_BancodeDados().setVisible(true);
        });
    }
}