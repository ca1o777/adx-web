package br.edu.adx;

import br.edu.adx.dao.Banco;
import br.edu.adx.dao.ErroBanco;
import br.edu.adx.util.Formatos;
import br.edu.adx.util.Regras;
import br.edu.adx.util.Seguranca;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * Popula um banco vazio com jogadores e partidas fictícios, apenas para demonstrar o ranking.
 * Use com a opção --demo. Esses jogadores têm senha aleatória e não conseguem fazer login.
 */
public final class DadosDemo {
    private record Jogador(String nome, int vitorias, int empates, int derrotas) { }

    private static final List<Jogador> JOGADORES = List.of(
            new Jogador("Juliana Prates", 19, 5, 6),
            new Jogador("Rafael Moura", 16, 4, 7),
            new Jogador("Bruno Tavares", 11, 3, 8),
            new Jogador("Letícia Andrade", 9, 6, 4),
            new Jogador("Marcos Vilela", 8, 2, 10),
            new Jogador("Paula Ribeiro", 6, 3, 6),
            new Jogador("Diego Sampaio", 5, 4, 8));

    private DadosDemo() { }

    public static void popular() {
        Random sorteio = new Random(2026);
        String hash = Seguranca.gerarHash(UUID.randomUUID().toString());
        LocalDateTime agora = Formatos.agora();
        // Uma falha durante a carga não deve deixar somente parte dos jogadores.
        try (Connection c = Banco.conexao()) {
            c.setAutoCommit(false);
            try (Statement verificar = c.createStatement();
                 ResultSet rs = verificar.executeQuery("SELECT COUNT(*) FROM usuarios")) {
                rs.next();
                if (rs.getInt(1) > 0) {
                    c.rollback();
                    return;
                }
            }
            try (PreparedStatement usuario = c.prepareStatement(
                    "INSERT INTO usuarios (nome, email, senha_hash, nome_exibicao, criado_em) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement partida = c.prepareStatement(
                    "INSERT INTO partidas (usuario_id, adversario, modo, resultado, data_hora) VALUES (?, ?, ?, ?, ?)")) {
                for (Jogador j : JOGADORES) {
                    String email = Normalizer.normalize(j.nome(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                            .toLowerCase(Locale.ROOT).replace(' ', '.') + "@demo.adx";
                    usuario.setString(1, j.nome());
                    usuario.setString(2, email);
                    usuario.setString(3, hash);
                    usuario.setString(4, j.nome());
                    usuario.setObject(5, agora);
                    usuario.executeUpdate();
                    long id;
                    try (ResultSet keys = usuario.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Não foi possível identificar o jogador de demonstração.");
                        }
                        id = keys.getLong(1);
                    }

                    List<String> resultados = new ArrayList<>();
                    resultados.addAll(Collections.nCopies(j.vitorias(), "Vitória"));
                    resultados.addAll(Collections.nCopies(j.empates(), "Empate"));
                    resultados.addAll(Collections.nCopies(j.derrotas(), "Derrota"));
                    Collections.shuffle(resultados, sorteio);

                    for (String resultado : resultados) {
                        String adversario = JOGADORES.get(sorteio.nextInt(JOGADORES.size())).nome();
                        if (adversario.equals(j.nome())) {
                            adversario = "Caio Fernandes";
                        }
                        String modo = Regras.MODOS.get(sorteio.nextInt(Regras.MODOS.size()));
                        LocalDateTime data = agora.minusDays(sorteio.nextInt(90)).minusMinutes(sorteio.nextInt(1440));
                        partida.setLong(1, id);
                        partida.setString(2, adversario);
                        partida.setString(3, modo);
                        partida.setString(4, resultado);
                        partida.setObject(5, data);
                        partida.executeUpdate();
                    }
                }
                c.commit();
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
        System.out.println("Dados de demonstracao criados: " + JOGADORES.size() + " jogadores.");
    }
}
