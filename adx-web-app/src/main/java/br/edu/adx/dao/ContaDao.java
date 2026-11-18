package br.edu.adx.dao;

import br.edu.adx.util.Seguranca;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/** Códigos de uso único associados à conta e à operação que o jogador solicitou. */
public class ContaDao {
    public static final int VALIDADE_MINUTOS = 15;
    private static final int REENVIO_SEGUNDOS = 60;
    private static final int TENTATIVAS_MAXIMAS = 5;
    private static final String ALFABETO = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private final Clock relogio;

    public enum Estado { PRONTO, CONFIRMADO, AGUARDE, INVALIDO, SESSAO_EXPIRADA, EMAIL_IGUAL, EMAIL_EM_USO }

    public record Envio(String destino, String codigo) {
        @Override
        public String toString() {
            return "Envio de código de confirmação";
        }
    }

    public record Solicitacao(Estado estado, Envio envio) { }

    private enum Finalidade { EMAIL, SENHA }
    private record Conta(long id, String email) { }
    private record Codigo(byte[] hash, String novoEmail, OffsetDateTime enviadoEm,
                          OffsetDateTime expiraEm, int tentativas) { }

    public ContaDao() {
        this(Clock.systemUTC());
    }

    ContaDao(Clock relogio) {
        this.relogio = relogio;
    }

    public Solicitacao solicitarEmail(long id, long versao, String email) {
        return transacao(c -> {
            Conta conta = contaDaSessao(c, id, versao);
            if (conta == null) {
                return new Solicitacao(Estado.SESSAO_EXPIRADA, null);
            }
            if (conta.email().equals(email)) {
                return new Solicitacao(Estado.EMAIL_IGUAL, null);
            }
            if (emailEmUso(c, email)) {
                return new Solicitacao(Estado.EMAIL_EM_USO, null);
            }
            Envio envio = gerarEnvio(c, conta, Finalidade.EMAIL, email);
            return new Solicitacao(envio == null ? Estado.AGUARDE : Estado.PRONTO, envio);
        });
    }

    public Optional<Envio> solicitarSenha(String email) {
        return transacao(c -> {
            Conta conta = contaPorEmail(c, email);
            return conta == null ? Optional.empty() : Optional.ofNullable(gerarEnvio(c, conta, Finalidade.SENHA, null));
        });
    }

    public Estado confirmarEmail(long id, long versao, String codigo) {
        return transacao(c -> {
            Conta conta = contaDaSessao(c, id, versao);
            if (conta == null) {
                return Estado.SESSAO_EXPIRADA;
            }
            Codigo registro = validarCodigo(c, id, Finalidade.EMAIL, codigo);
            if (registro == null) {
                return Estado.INVALIDO;
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE usuarios SET email = ? WHERE id = ?")) {
                ps.setString(1, registro.novoEmail());
                ps.setLong(2, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                if (!"23505".equals(e.getSQLState())) {
                    throw e;
                }
                apagarCodigos(c, id, Finalidade.EMAIL);
                return Estado.EMAIL_EM_USO;
            }
            apagarCodigos(c, id, null);
            return Estado.CONFIRMADO;
        });
    }

    public Estado confirmarSenha(String email, String codigo, String novaSenha) {
        return transacao(c -> {
            Conta conta = contaPorEmail(c, email);
            if (conta == null || validarCodigo(c, conta.id(), Finalidade.SENHA, codigo) == null) {
                return Estado.INVALIDO;
            }
            String sql = """
                UPDATE usuarios
                SET senha_hash = ?, falhas_login = 0, bloqueado_ate = NULL, versao_credencial = versao_credencial + 1
                WHERE id = ?
                """;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, Seguranca.gerarHash(novaSenha));
                ps.setLong(2, conta.id());
                ps.executeUpdate();
            }
            apagarCodigos(c, conta.id(), null);
            return Estado.CONFIRMADO;
        });
    }

    private Envio gerarEnvio(Connection c, Conta conta, Finalidade finalidade, String novoEmail) throws SQLException {
        Codigo anterior = codigo(c, conta.id(), finalidade);
        OffsetDateTime agora = agora();
        if (anterior != null && anterior.enviadoEm().plusSeconds(REENVIO_SEGUNDOS).isAfter(agora)) {
            return null;
        }
        StringBuilder codigo = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            codigo.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
        }
        String texto = codigo.toString();
        String sql = """
            MERGE INTO codigos_conta (usuario_id, finalidade, codigo_hash, novo_email, enviado_em, expira_em, tentativas)
            KEY (usuario_id, finalidade) VALUES (?, ?, ?, ?, ?, ?, 0)
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, conta.id());
            ps.setString(2, finalidade.name());
            ps.setBytes(3, hash(texto));
            ps.setString(4, novoEmail);
            ps.setObject(5, agora);
            ps.setObject(6, agora.plusMinutes(VALIDADE_MINUTOS));
            ps.executeUpdate();
        }
        return new Envio(conta.email(), texto);
    }

    private Codigo validarCodigo(Connection c, long id, Finalidade finalidade, String fornecido) throws SQLException {
        Codigo registro = codigo(c, id, finalidade);
        if (registro == null || !registro.expiraEm().isAfter(agora()) || registro.tentativas() >= TENTATIVAS_MAXIMAS) {
            return null;
        }
        if (MessageDigest.isEqual(registro.hash(), hash(fornecido))) {
            return registro;
        }
        String sql = "UPDATE codigos_conta SET tentativas = tentativas + 1 WHERE usuario_id = ? AND finalidade = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setString(2, finalidade.name());
            ps.executeUpdate();
        }
        return null;
    }

    private static Codigo codigo(Connection c, long id, Finalidade finalidade) throws SQLException {
        String sql = """
            SELECT codigo_hash, novo_email, enviado_em, expira_em, tentativas
            FROM codigos_conta WHERE usuario_id = ? AND finalidade = ?
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setString(2, finalidade.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Codigo(rs.getBytes(1), rs.getString(2),
                        rs.getObject(3, OffsetDateTime.class), rs.getObject(4, OffsetDateTime.class), rs.getInt(5)) : null;
            }
        }
    }

    private static Conta contaDaSessao(Connection c, long id, long versao) throws SQLException {
        String sql = "SELECT id, email FROM usuarios WHERE id = ? AND versao_credencial = ? FOR UPDATE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setLong(2, versao);
            return conta(ps);
        }
    }

    private static Conta contaPorEmail(Connection c, String email) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id, email FROM usuarios WHERE email = ? FOR UPDATE")) {
            ps.setString(1, email);
            return conta(ps);
        }
    }

    private static Conta conta(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? new Conta(rs.getLong(1), rs.getString(2)) : null;
        }
    }

    private static boolean emailEmUso(Connection c, String email) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM usuarios WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void apagarCodigos(Connection c, long id, Finalidade finalidade) throws SQLException {
        String sql = "DELETE FROM codigos_conta WHERE usuario_id = ?" + (finalidade == null ? "" : " AND finalidade = ?");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            if (finalidade != null) {
                ps.setString(2, finalidade.name());
            }
            ps.executeUpdate();
        }
    }

    private OffsetDateTime agora() {
        return relogio.instant().atOffset(ZoneOffset.UTC);
    }

    private static byte[] hash(String codigo) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(codigo.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    @FunctionalInterface
    private interface Operacao<T> {
        T executar(Connection c) throws SQLException;
    }

    private static <T> T transacao(Operacao<T> operacao) {
        try (Connection c = Banco.conexao()) {
            c.setAutoCommit(false);
            try {
                // Cada operação trava primeiro a linha do usuário, inclusive quando ainda não há um código.
                T resultado = operacao.executar(c);
                c.commit();
                return resultado;
            } catch (SQLException | RuntimeException e) {
                try {
                    c.rollback();
                } catch (SQLException falhaRollback) {
                    e.addSuppressed(falhaRollback);
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new ErroBanco(e);
        }
    }
}
