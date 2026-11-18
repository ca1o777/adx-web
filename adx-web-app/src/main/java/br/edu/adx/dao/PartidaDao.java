package br.edu.adx.dao;

import br.edu.adx.model.Contagem;
import br.edu.adx.model.Partida;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Collator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Acesso às partidas. Não existe operação de exclusão nem de alteração:
 * partidas registradas só podem ser consultadas (RN002 e RN004).
 */
public class PartidaDao {
    public record Pagina(int total, List<Partida> itens) { }

    /** Numera as partidas de cada jogador em ordem cronológica (nº da partida no histórico). */
    private static final String INTERNA = """
        (SELECT p.id, p.adversario, p.modo, p.resultado, p.data_hora,
                COALESCE(p.data_partida, CAST(p.data_hora AS DATE)) AS data_partida,
                ROW_NUMBER() OVER (ORDER BY p.data_hora, p.id) AS numero
           FROM partidas p WHERE p.usuario_id = ?) t""";
    private static final String SELECIONAR =
        "SELECT id, adversario, modo, resultado, data_hora, data_partida, numero FROM " + INTERNA;
    private static final String FILTRO =
        " WHERE (? = '' OR LOWER(adversario) LIKE ? ESCAPE '\\') AND (? = '' OR modo = ?) AND (? = '' OR resultado = ?)";

    public long inserir(long usuarioId, String adversario, String modo, String resultado, LocalDateTime dataHora) {
        return inserir(usuarioId, adversario, modo, resultado, dataHora, dataHora.toLocalDate());
    }

    public long inserir(long usuarioId, String adversario, String modo, String resultado,
                        LocalDateTime dataHora, LocalDate dataPartida) {
        String sql = "INSERT INTO partidas (usuario_id, adversario, modo, resultado, data_hora, data_partida) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, usuarioId);
            ps.setString(2, adversario);
            ps.setString(3, modo);
            ps.setString(4, resultado);
            ps.setObject(5, dataHora);
            ps.setObject(6, dataPartida);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new ErroBanco(e);
        }
    }

    public Pagina listar(long usuarioId, String busca, String modo, String resultado, int pagina, int porPagina) {
        if (pagina < 1 || porPagina < 1) {
            throw new IllegalArgumentException("Página e tamanho da página devem ser positivos.");
        }
        long deslocamento = ((long) pagina - 1) * porPagina;
        try (Connection c = Banco.conexao()) {
            int total;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM " + INTERNA + FILTRO)) {
                ps.setLong(1, usuarioId);
                filtrar(ps, 2, busca, modo, resultado);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getInt(1);
                }
            }
            // Uma página além do total não precisa executar a consulta dos itens.
            if (deslocamento >= total) {
                return new Pagina(total, List.of());
            }
            List<Partida> itens = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(SELECIONAR + FILTRO + " ORDER BY numero DESC LIMIT ? OFFSET ?")) {
                ps.setLong(1, usuarioId);
                int i = filtrar(ps, 2, busca, modo, resultado);
                ps.setInt(i++, porPagina);
                ps.setLong(i, deslocamento);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        itens.add(mapear(rs));
                    }
                }
            }
            return new Pagina(total, itens);
        } catch (SQLException e) {
            throw new ErroBanco(e);
        }
    }

    public Optional<Partida> buscar(long usuarioId, long id) {
        try (Connection c = Banco.conexao(); PreparedStatement ps = c.prepareStatement(SELECIONAR + " WHERE id = ?")) {
            ps.setLong(1, usuarioId);
            ps.setLong(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ErroBanco(e);
        }
    }

    /** Nomes para o registro de partidas: jogadores cadastrados e adversários do próprio histórico. */
    public List<String> adversarios(long usuarioId) {
        String sql = """
            SELECT COALESCE(MIN(CASE WHEN cadastrado THEN nome END), MIN(nome))
            FROM (
                SELECT nome_exibicao AS nome, TRUE AS cadastrado FROM usuarios WHERE id <> ?
                UNION ALL
                SELECT adversario, FALSE FROM partidas WHERE usuario_id = ?
            ) candidatos
            GROUP BY LOWER(nome)
            """;
        try (Connection c = Banco.conexao(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, usuarioId);
            ps.setLong(2, usuarioId);
            List<String> nomes = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    nomes.add(rs.getString(1));
                }
            }
            nomes.sort(Collator.getInstance(Locale.forLanguageTag("pt-BR")));
            return nomes;
        } catch (SQLException e) {
            throw new ErroBanco(e);
        }
    }

    /** Todas as partidas do jogador contra o mesmo adversário (retrospecto). */
    public List<Partida> contra(long usuarioId, String adversario) {
        String sql = SELECIONAR + " WHERE LOWER(adversario) = LOWER(?) ORDER BY numero DESC";
        try (Connection c = Banco.conexao(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, usuarioId);
            ps.setString(2, adversario);
            return ler(ps);
        } catch (SQLException e) {
            throw new ErroBanco(e);
        }
    }

    public List<Partida> ultimas(long usuarioId, int quantidade) {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(SELECIONAR + " ORDER BY numero DESC LIMIT ?")) {
            ps.setLong(1, usuarioId);
            ps.setInt(2, quantidade);
            return ler(ps);
        } catch (SQLException e) {
            throw new ErroBanco(e);
        }
    }

    /** Totais do jogador; {@code modo} vazio significa todos os modos. */
    public Contagem contagem(long usuarioId, String modo) {
        String sql = """
            SELECT COUNT(*),
                   COALESCE(SUM(CASE WHEN resultado = 'Vitória' THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN resultado = 'Empate'  THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN resultado = 'Derrota' THEN 1 ELSE 0 END), 0)
            FROM partidas WHERE usuario_id = ? AND (? = '' OR modo = ?)
            """;
        try (Connection c = Banco.conexao(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, usuarioId);
            ps.setString(2, modo);
            ps.setString(3, modo);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new Contagem(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4));
            }
        } catch (SQLException e) {
            throw new ErroBanco(e);
        }
    }

    private static List<Partida> ler(PreparedStatement ps) throws SQLException {
        List<Partida> lista = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    private static int filtrar(PreparedStatement ps, int i, String busca, String modo, String resultado) throws SQLException {
        String termo = busca.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        ps.setString(i++, busca);
        ps.setString(i++, "%" + termo + "%");
        ps.setString(i++, modo);
        ps.setString(i++, modo);
        ps.setString(i++, resultado);
        ps.setString(i++, resultado);
        return i;
    }

    private static Partida mapear(ResultSet rs) throws SQLException {
        return new Partida(rs.getLong("id"), rs.getLong("numero"), rs.getString("adversario"),
                rs.getString("modo"), rs.getString("resultado"), rs.getObject("data_hora", LocalDateTime.class),
                rs.getObject("data_partida", LocalDate.class));
    }
}
