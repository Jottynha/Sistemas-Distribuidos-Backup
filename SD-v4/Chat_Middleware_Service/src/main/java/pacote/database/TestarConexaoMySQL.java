package pacote.database;

import java.sql.SQLException;

/**
 * Teste manual da configuracao JDBC e da tabela mensagens.
 * No NetBeans, use Run File nesta classe depois de configurar as variaveis.
 */
public final class TestarConexaoMySQL {

    private TestarConexaoMySQL() {
    }

    public static void main(String[] args) {
        try {
            System.out.println("========================================");
            System.out.println("TESTE DE CONEXAO COM MYSQL");
            System.out.println("========================================");

            System.out.println(DatabaseConnection.testarConexao());

            MensagemDAO dao = new MensagemDAO();
            System.out.println(
                    "Tabela mensagens acessivel. Registros: "
                    + dao.contarMensagens()
            );

            System.out.println("[SUCESSO] Conexao JDBC validada.");

        } catch (SQLException | RuntimeException erro) {
            System.err.println("[FALHA] Nao foi possivel validar o MySQL.");
            System.err.println(erro.getClass().getSimpleName() + ": " + erro.getMessage());
            erro.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
