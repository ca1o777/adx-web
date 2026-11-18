package br.edu.adx;

import static org.junit.jupiter.api.Assertions.*;

import br.edu.adx.dao.Banco;
import br.edu.adx.dao.ErroBanco;
import br.edu.adx.dao.UsuarioDao;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

/** A carga de demonstração deve ocorrer integralmente, preservando bancos existentes. */
@ResourceLock("br.edu.adx.dao.Banco")
class DadosDemoTest {
    private static String bancoAnterior;

    @BeforeAll
    static void iniciar() {
        bancoAnterior = System.getProperty("adx.db");
        System.setProperty("adx.db", "jdbc:h2:mem:demo-test-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        Banco.iniciar();
    }

    @BeforeEach
    void limpar() throws SQLException {
        try (Connection c = Banco.conexao(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM partidas");
            st.executeUpdate("DELETE FROM usuarios");
        }
    }

    @AfterAll
    static void encerrar() throws SQLException {
        try (Connection c = Banco.conexao(); Statement st = c.createStatement()) {
            st.execute("SHUTDOWN");
        } finally {
            if (bancoAnterior == null) System.clearProperty("adx.db");
            else System.setProperty("adx.db", bancoAnterior);
        }
    }

    @Test
    void carregaTodosOsSeteJogadoresESuas150Partidas() throws SQLException {
        DadosDemo.popular();
        assertEquals(7, contar("usuarios"));
        assertEquals(150, contar("partidas"));
        var ranking = new UsuarioDao().ranking();
        assertEquals("Juliana Prates", ranking.get(0).nome());
        assertEquals(19, ranking.get(0).contagem().vitorias());
    }

    @Test
    void repetirCargaNaoDuplicaDados() throws SQLException {
        DadosDemo.popular();
        DadosDemo.popular();
        assertEquals(7, contar("usuarios"));
        assertEquals(150, contar("partidas"));
    }

    @Test
    void preservaBancoQueJaContemJogadorReal() throws SQLException {
        long id = new UsuarioDao().inserir("Pessoa Real", "real@example.test", "hash-teste", "Pessoa");
        DadosDemo.popular();
        assertEquals(1, contar("usuarios"));
        assertEquals(0, contar("partidas"));
        assertEquals("Pessoa", new UsuarioDao().porId(id).orElseThrow().nomeExibicao());
    }

    @Test
    void falhaNaPrimeiraPartidaDesfazTambemOJogadorJaInserido() throws SQLException {
        try (Connection c = Banco.conexao(); Statement st = c.createStatement()) {
            st.execute("ALTER TABLE partidas ADD CONSTRAINT rejeitar_demo_teste CHECK (1 = 0)");
        }
        try {
            assertThrows(ErroBanco.class, DadosDemo::popular);
            assertEquals(0, contar("usuarios"), "Uma carga interrompida não deve deixar jogadores sem partidas");
            assertEquals(0, contar("partidas"));
        } finally {
            try (Connection c = Banco.conexao(); Statement st = c.createStatement()) {
                st.execute("ALTER TABLE partidas DROP CONSTRAINT rejeitar_demo_teste");
            }
        }
    }

    private static int contar(String tabela) throws SQLException {
        try (Connection c = Banco.conexao(); Statement st = c.createStatement();
             var rs = st.executeQuery("SELECT COUNT(*) FROM " + tabela)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
