package br.edu.adx.dao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("br.edu.adx.dao.Banco")
class FotoDaoTest {
    @Test
    void migraBancoExistenteEIsolaFotosAoSalvarSubstituirERemover() throws SQLException {
        String bancoAnterior = System.getProperty("adx.db");
        System.setProperty("adx.db", "jdbc:h2:mem:adx-foto-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        Banco.iniciar();
        try {
            UsuarioDao usuarios = new UsuarioDao();
            long ana = usuarios.inserir("Ana Silva", "ana@example.com", "hash-teste", "Ana");
            long eva = usuarios.inserir("Eva Lopes", "eva@example.com", "hash-teste", "Eva");
            FotoDao.iniciar();
            FotoDao.iniciar();

            FotoDao fotos = new FotoDao();
            assertTrue(fotos.porUsuario(ana).isEmpty());
            fotos.salvar(ana, new byte[] { 1, 2, 3 });
            fotos.salvar(eva, new byte[] { 4, 5, 6 });
            fotos.salvar(ana, new byte[] { 7, 8 });
            assertArrayEquals(new byte[] { 7, 8 }, new FotoDao().porUsuario(ana).orElseThrow());
            assertArrayEquals(new byte[] { 4, 5, 6 }, fotos.porUsuario(eva).orElseThrow());
            fotos.remover(ana);
            fotos.remover(ana);
            assertTrue(fotos.porUsuario(ana).isEmpty());
            assertArrayEquals(new byte[] { 4, 5, 6 }, fotos.porUsuario(eva).orElseThrow());
        } finally {
            try (Connection c = Banco.conexao(); Statement st = c.createStatement()) {
                st.execute("SHUTDOWN");
            } finally {
                if (bancoAnterior == null) {
                    System.clearProperty("adx.db");
                } else {
                    System.setProperty("adx.db", bancoAnterior);
                }
            }
        }
    }
}
