package br.edu.adx.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/** Fotos normalizadas, separadas dos dados consultados nas telas e no ranking. */
public final class FotoDao {
    public static void iniciar() {
        String sql = """
                CREATE TABLE IF NOT EXISTS fotos_perfil (
                  usuario_id BIGINT PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
                  png BLOB NOT NULL
                )
                """;
        try (Connection c = Banco.conexao(); Statement st = c.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new ErroBanco(e);
        }
    }

    public Optional<byte[]> porUsuario(long usuarioId) {
        String sql = "SELECT png FROM fotos_perfil WHERE usuario_id = ?";
        try (Connection c = Banco.conexao(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getBytes(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ErroBanco(e);
        }
    }

    public void salvar(long usuarioId, byte[] png) {
        String sql = "MERGE INTO fotos_perfil (usuario_id, png) KEY (usuario_id) VALUES (?, ?)";
        try (Connection c = Banco.conexao(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, usuarioId);
            ps.setBytes(2, png);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBanco(e);
        }
    }

    public void remover(long usuarioId) {
        String sql = "DELETE FROM fotos_perfil WHERE usuario_id = ?";
        try (Connection c = Banco.conexao(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, usuarioId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBanco(e);
        }
    }
}
