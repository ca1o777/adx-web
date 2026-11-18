package br.edu.adx.dao;

import br.edu.adx.model.Contagem;
import br.edu.adx.model.LinhaRanking;
import br.edu.adx.model.Usuario;
import br.edu.adx.util.Formatos;
import br.edu.adx.util.Regras;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class UsuarioDao {

    /** Dados necessários para conferir a senha e aplicar o bloqueio por tentativas (RN003). */
    public record Credencial(long id, String hash, int falhas, LocalDateTime bloqueadoAte) { }

    /** Resultado persistido da tentativa: autorizado, senha incorreta ou bloqueio até a data indicada. */
    public record Autenticacao(long usuarioId, boolean autorizado, LocalDateTime bloqueadoAte,
                               long versaoCredencial) { }

    public long inserir(String nome, String email, String hash, String nomeExibicao) {
        String sql = "INSERT INTO usuarios (nome, email, senha_hash, nome_exibicao, criado_em) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nome);
            ps.setString(2, email);
            ps.setString(3, hash);
            ps.setString(4, nomeExibicao);
            ps.setObject(5, Formatos.agora());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new ErroBanco(e);
        }
    }

    public boolean emailExiste(String email) {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM usuarios WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new ErroBanco(e);
        }
    }

    public int contar() {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM usuarios");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new ErroBanco(e);
        }
    }

    public Optional<Credencial> credencial(String email) {
        String sql = "SELECT id, senha_hash, falhas_login, bloqueado_ate FROM usuarios WHERE email = ?";
        try (Connection c = Banco.conexao(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Credencial(rs.getLong(1), rs.getString(2), rs.getInt(3),
                        rs.getObject(4, LocalDateTime.class)));
            }
        } catch (SQLException e) {
            throw new ErroBanco(e);
        }
    }

    /**
     * RN003: serializa as tentativas da mesma conta, incluindo a conferência da senha.
     * O bloqueio da linha vale para todas as conexões; contas diferentes continuam independentes.
     * O chamador fornece o verificador para manter o algoritmo de senha fora do DAO.
     */
    public Optional<Autenticacao> autenticar(String email, Predicate<String> conferirSenha) {
        return autenticar("email = ?", new Object[] {email}, conferirSenha);
    }

    /** Reconfirma a senha em uma sessão vigente e aplica o mesmo limite de tentativas do login. */
    public Optional<Autenticacao> reautenticar(long id, long versao, Predicate<String> conferirSenha) {
        return autenticar("id = ? AND versao_credencial = ?", new Object[] {id, versao}, conferirSenha);
    }

    private Optional<Autenticacao> autenticar(String filtro, Object[] parametros, Predicate<String> conferirSenha) {
        String sql = "SELECT id, senha_hash, falhas_login, bloqueado_ate, versao_credencial FROM usuarios WHERE "
                + filtro + " FOR UPDATE";
        try (Connection c = Banco.conexao()) {
            c.setAutoCommit(false);
            try {
                Credencial cred = null;
                long versao = 0;
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    for (int i = 0; i < parametros.length; i++) {
                        ps.setObject(i + 1, parametros[i]);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            cred = new Credencial(rs.getLong(1), rs.getString(2), rs.getInt(3),
                                    rs.getObject(4, LocalDateTime.class));
                            versao = rs.getLong(5);
                        }
                    }
                }
                if (cred == null) {
                    c.commit();
                    return Optional.empty();
                }

                // A hora é obtida depois de adquirir a linha: o bloqueio pode expirar durante a espera.
                LocalDateTime agora = Formatos.agora();
                if (cred.bloqueadoAte() != null && cred.bloqueadoAte().isAfter(agora)) {
                    c.commit();
                    return Optional.of(new Autenticacao(cred.id(), false, cred.bloqueadoAte(), versao));
                }

                boolean autorizado = conferirSenha.test(cred.hash());
                int falhas = autorizado ? 0 : cred.falhas() + 1;
                LocalDateTime bloqueadoAte = null;
                if (falhas >= Regras.TENTATIVAS_MAXIMAS) {
                    falhas = 0;
                    bloqueadoAte = Formatos.agora().plusMinutes(Regras.BLOQUEIO_MINUTOS);
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE usuarios SET falhas_login = ?, bloqueado_ate = ? WHERE id = ?")) {
                    ps.setInt(1, falhas);
                    ps.setObject(2, bloqueadoAte, Types.TIMESTAMP);
                    ps.setLong(3, cred.id());
                    ps.executeUpdate();
                }
                c.commit();
                return Optional.of(new Autenticacao(cred.id(), autorizado, bloqueadoAte, versao));
            } catch (SQLException | RuntimeException e) {
                try {
                    c.rollback();
                } catch (SQLException rollback) {
                    e.addSuppressed(rollback);
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new ErroBanco(e);
        }
    }

    public boolean sessaoAtual(long id, long versao) {
        String sql = "SELECT 1 FROM usuarios WHERE id = ? AND versao_credencial = ?";
        try (Connection c = Banco.conexao(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setLong(2, versao);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new ErroBanco(e);
        }
    }

    public void registrarFalha(long id, int falhas, LocalDateTime bloqueadoAte) {
        String sql = "UPDATE usuarios SET falhas_login = ?, bloqueado_ate = ? WHERE id = ?";
        try (Connection c = Banco.conexao(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, falhas);
            ps.setObject(2, bloqueadoAte, Types.TIMESTAMP);
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBanco(e);
        }
    }

    public void zerarFalhas(long id) {
        registrarFalha(id, 0, null);
    }

    public Optional<Usuario> porId(long id) {
        String sql = "SELECT id, nome, email, nome_exibicao, modo_preferido, sobre FROM usuarios WHERE id = ?";
        try (Connection c = Banco.conexao(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Usuario(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6)));
            }
        } catch (SQLException e) {
            throw new ErroBanco(e);
        }
    }

    public void atualizarPerfil(long id, String nomeExibicao, String modoPreferido, String sobre) {
        String sql = "UPDATE usuarios SET nome_exibicao = ?, modo_preferido = ?, sobre = ? WHERE id = ?";
        try (Connection c = Banco.conexao(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nomeExibicao);
            ps.setString(2, modoPreferido);
            ps.setString(3, sobre);
            ps.setLong(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErroBanco(e);
        }
    }

    /** Ranking: mais vitórias primeiro; em caso de empate, maior aproveitamento (RN002). */
    public List<LinhaRanking> ranking() {
        String sql = """
            SELECT u.id, u.nome_exibicao,
                   COUNT(p.id),
                   COALESCE(SUM(CASE WHEN p.resultado = 'Vitória' THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN p.resultado = 'Empate'  THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN p.resultado = 'Derrota' THEN 1 ELSE 0 END), 0)
            FROM usuarios u LEFT JOIN partidas p ON p.usuario_id = u.id
            GROUP BY u.id, u.nome_exibicao
            """;
        List<LinhaRanking> linhas = new ArrayList<>();
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                linhas.add(new LinhaRanking(rs.getLong(1), rs.getString(2),
                        new Contagem(rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6))));
            }
        } catch (SQLException e) {
            throw new ErroBanco(e);
        }
        linhas.sort(Comparator.comparingInt((LinhaRanking l) -> l.contagem().vitorias()).reversed()
                .thenComparing((a, b) -> compararAproveitamento(a.contagem(), b.contagem()))
                .thenComparing(LinhaRanking::nome, String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(LinhaRanking::usuarioId));
        return linhas;
    }

    /** Compara as frações reais em ordem decrescente, sem usar o percentual arredondado da tela. */
    private static int compararAproveitamento(Contagem a, Contagem b) {
        long pontosA = 2L * a.vitorias() + a.empates();
        long pontosB = 2L * b.vitorias() + b.empates();
        return Long.compare(pontosB * Math.max(1, a.total()), pontosA * Math.max(1, b.total()));
    }
}
