package pacote.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Ponto central para abertura de conexoes JDBC com o MySQL.
 * Cada chamada retorna uma conexao nova, que deve ser fechada por quem a utiliza.
 */
public final class DatabaseConnection {

    private DatabaseConnection() {
    }

    public static Connection abrirConexao() throws SQLException {
        final DatabaseConfig config;

        try {
            config = DatabaseConfig.carregar();
        } catch (RuntimeException erro) {
            throw new SQLException(
                    "Configuracao do MySQL invalida ou incompleta: "
                    + erro.getMessage(),
                    erro
            );
        }

        return DriverManager.getConnection(
                config.jdbcUrl(),
                config.username(),
                config.password()
        );
    }

    /**
     * Executa uma consulta simples para validar a conexao e o banco selecionado.
     * Retorna a versao informada pelo servidor MySQL.
     */
    public static String testarConexao() throws SQLException {
        String sql = "SELECT VERSION(), DATABASE()";

        try (Connection conexao = abrirConexao();
             Statement comando = conexao.createStatement();
             ResultSet resultado = comando.executeQuery(sql)) {

            if (!resultado.next()) {
                throw new SQLException(
                        "O MySQL nao retornou os dados de identificacao."
                );
            }

            return "MySQL " + resultado.getString(1)
                    + " | Banco: " + resultado.getString(2);
        }
    }
}
