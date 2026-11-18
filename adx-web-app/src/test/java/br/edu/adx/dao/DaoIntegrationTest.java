package br.edu.adx.dao;

import static org.junit.jupiter.api.Assertions.*;

import br.edu.adx.model.Contagem;
import br.edu.adx.model.LinhaRanking;
import br.edu.adx.model.Partida;
import br.edu.adx.model.Usuario;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercita os DAOs reais e as restrições do H2, sem servidor HTTP ou banco em arquivo. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("br.edu.adx.dao.Banco")
class DaoIntegrationTest {
    private static final LocalDateTime DATA = LocalDateTime.of(2026, 1, 15, 12, 0);
    private final UsuarioDao usuarios = new UsuarioDao();
    private final PartidaDao partidas = new PartidaDao();
    private String bancoAnterior;
    private long ana;
    private long outro;
    private int sequencia;

    @BeforeAll
    void iniciarBancoIsolado() {
        bancoAnterior = System.getProperty("adx.db");
        System.setProperty("adx.db", "jdbc:h2:mem:adx-test-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        // Banco mantém pool estático: inicializamos uma única vez para a classe inteira.
        Banco.iniciar();
    }

    @BeforeEach
    void limparDados() throws SQLException {
        try (Connection c = Banco.conexao(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM partidas");
            st.executeUpdate("DELETE FROM usuarios");
        }
        sequencia = 0;
        ana = usuarios.inserir("Ana Silva", "ana@example.com", "hash-de-teste", "Ana");
        outro = usuarios.inserir("Outro Jogador", "outro@example.com", "outro-hash", "Outro");
    }

    @AfterAll
    void encerrarBancoERestaurarPropriedade() throws SQLException {
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

    @Test
    void partidaSemDataSeparadaUsaDataOriginalDoRegistro() throws SQLException {
        String sql = "INSERT INTO partidas (usuario_id, adversario, modo, resultado, data_hora) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = Banco.conexao(); var ps = c.prepareStatement(sql)) {
            ps.setLong(1, ana);
            ps.setString(2, "Adversário anterior");
            ps.setString(3, "Blitz");
            ps.setString(4, "Empate");
            ps.setObject(5, DATA);
            ps.executeUpdate();
        }
        Partida partida = partidas.ultimas(ana, 1).get(0);
        assertEquals(DATA, partida.dataHora());
        assertEquals(DATA.toLocalDate(), partida.dataPartida());
    }

    @Test
    void cadastraConsultaEContaUsuariosSemExporHashNoPerfil() {
        assertEquals(2, usuarios.contar());
        assertTrue(usuarios.emailExiste("ana@example.com"));
        assertFalse(usuarios.emailExiste("ausente@example.com"));
        assertTrue(usuarios.credencial("ausente@example.com").isEmpty());
        Usuario usuario = usuarios.porId(ana).orElseThrow();
        assertEquals(new Usuario(ana, "Ana Silva", "ana@example.com", "Ana", "Clássico", ""), usuario);
        var credencial = usuarios.credencial("ana@example.com").orElseThrow();
        assertEquals(ana, credencial.id());
        assertEquals("hash-de-teste", credencial.hash());
        assertEquals(0, credencial.falhas());
        assertNull(credencial.bloqueadoAte());
        assertTrue(usuarios.porId(Long.MAX_VALUE).isEmpty());
    }

    @Test
    void emailDuplicadoViolaRestricaoUniqueSemInserirOutraConta() {
        ErroBanco erro = assertThrows(ErroBanco.class,
                () -> usuarios.inserir("Ana Outra", "ana@example.com", "hash", "Ana Outra"));
        assertTrue(erro.violacaoUnica());
        assertEquals("23505", ((SQLException) erro.getCause()).getSQLState());
        assertEquals(2, usuarios.contar());
    }

    @Test
    void partidaDeUsuarioInexistenteViolaChaveEstrangeira() {
        ErroBanco erro = assertThrows(ErroBanco.class,
                () -> inserir(Long.MAX_VALUE, "Adversário", "Blitz", "Vitória"));
        assertEquals("23506", ((SQLException) erro.getCause()).getSQLState());
        assertFalse(erro.violacaoUnica());
        assertEquals(0, partidas.contagem(ana, "").total());
    }

    @Test
    void chaveEstrangeiraImpedeApagarUsuarioQueTemPartidas() {
        inserir(ana, "Adversário", "Blitz", "Vitória");
        try (Connection c = Banco.conexao(); var ps = c.prepareStatement("DELETE FROM usuarios WHERE id = ?")) {
            ps.setLong(1, ana);
            SQLException erro = assertThrows(SQLException.class, ps::executeUpdate);
            assertEquals("23503", erro.getSQLState());
        } catch (SQLException erro) {
            fail(erro);
        }
        assertTrue(usuarios.porId(ana).isPresent());
        assertEquals(1, partidas.contagem(ana, "").total());
    }

    @ParameterizedTest
    @CsvSource({"Bullet,Vitória", "blitz,Vitória", "Blitz,Vitoria", "Blitz,Abandono"})
    void checkRejeitaModoOuResultadoForaDoDominio(String modo, String resultado) {
        ErroBanco erro = assertThrows(ErroBanco.class, () -> inserir(ana, "Adversário", modo, resultado));
        assertEquals("23513", ((SQLException) erro.getCause()).getSQLState());
        assertEquals(0, partidas.contagem(ana, "").total());
    }

    @Test
    void listaOutrosJogadoresEAdversariosDoProprioHistoricoSemRepetirNomes() {
        usuarios.inserir("Álvaro Ribeiro", "alvaro@example.com", "hash", "Álvaro");
        usuarios.inserir("Bia de Souza", "bia@example.com", "hash", "Bia");
        inserir(ana, "ÁLVARO", "Blitz", "Vitória");
        inserir(ana, "Carlos", "Blitz", "Empate");
        inserir(ana, "Carlos", "Rápido", "Vitória");
        inserir(outro, "Adversário particular", "Blitz", "Derrota");

        assertEquals(List.of("Álvaro", "Bia", "Carlos", "Outro"), partidas.adversarios(ana));
    }

    @Test
    void adversarioComMesmoNomeDoJogadorPodeSerSelecionado() {
        usuarios.inserir("Ana do Clube", "homonima@example.com", "hash", "Ana");
        assertEquals(List.of("Ana", "Outro"), partidas.adversarios(ana));
    }

    @Test
    void partidasEConsultasFicamIsoladasPorUsuario() {
        long minha = inserir(ana, "Mesmo Adversário", "Blitz", "Vitória");
        long alheia = inserir(outro, "Mesmo Adversário", "Blitz", "Derrota");
        assertTrue(partidas.buscar(ana, alheia).isEmpty());
        assertTrue(partidas.buscar(outro, minha).isEmpty());
        assertEquals(List.of(minha), ids(partidas.listar(ana, "", "", "", 1, 10).itens()));
        assertEquals(List.of(minha), ids(partidas.contra(ana, "mesmo adversário")));
        assertEquals(List.of(alheia), ids(partidas.ultimas(outro, 10)));
        assertEquals(new Contagem(1, 1, 0, 0), partidas.contagem(ana, ""));
        assertEquals(new Contagem(1, 0, 0, 1), partidas.contagem(outro, ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "\\"})
    void buscaTrataCaracteresLikeComoTextoLiteral(String busca) {
        // Os nomes semelhantes garantem que o teste detecte wildcard não escapado.
        for (String nome : List.of("Clube%Real", "Clube_Real", "Clube\\Real", "ClubeXReal")) {
            inserir(ana, nome, "Blitz", "Vitória");
        }
        var pagina = partidas.listar(ana, busca, "", "", 1, 10);
        assertEquals(1, pagina.total());
        assertEquals(List.of("Clube" + busca + "Real"), pagina.itens().stream().map(Partida::adversario).toList());
    }

    @Test
    void buscasComApostrofoETextoSqlNaoAlteramConsulta() {
        long apostrofo = inserir(ana, "D'Ávila", "Blitz", "Vitória");
        inserir(ana, "Outro Adversário", "Blitz", "Derrota");
        assertEquals(List.of(apostrofo), ids(partidas.listar(ana, "d'ávila", "", "", 1, 10).itens()));
        assertEquals(0, partidas.listar(ana, "' OR 1=1 --", "", "", 1, 10).total());
        assertEquals(2, usuarios.contar());
    }

    @Test
    void aplicaBuscaModoEResultadoEmConjunto() {
        inserir(ana, "Carlos", "Clássico", "Vitória");
        long esperado = inserir(ana, "CARLOS", "Blitz", "Vitória");
        inserir(ana, "Carlos", "Blitz", "Derrota");
        inserir(ana, "Bruno", "Blitz", "Vitória");
        inserir(outro, "Carlos", "Blitz", "Vitória");
        var pagina = partidas.listar(ana, "cArLo", "Blitz", "Vitória", 1, 10);
        assertEquals(1, pagina.total());
        assertEquals(List.of(esperado), ids(pagina.itens()));
        assertEquals(2, pagina.itens().get(0).numero());
    }

    @Test
    void numeracaoUsaCronologiaEIdDesempatandoDatasIguais() {
        long posterior = partidas.inserir(ana, "Posterior", "Blitz", "Vitória", DATA.plusDays(1));
        long primeiro = partidas.inserir(ana, "Primeiro", "Blitz", "Empate", DATA);
        long segundo = partidas.inserir(ana, "Segundo", "Blitz", "Derrota", DATA);
        List<Partida> lista = partidas.listar(ana, "", "", "", 1, 10).itens();
        assertEquals(List.of(posterior, segundo, primeiro), ids(lista));
        assertEquals(List.of(3L, 2L, 1L), lista.stream().map(Partida::numero).toList());
        assertEquals(DATA, partidas.buscar(ana, primeiro).orElseThrow().dataHora());
    }

    @Test
    void paginacaoPreservaTotalSemRepetirOuPerderItens() {
        long a = inserir(ana, "Adversário A", "Blitz", "Vitória");
        long b = inserir(ana, "Adversário B", "Blitz", "Empate");
        long c = inserir(ana, "Adversário C", "Blitz", "Derrota");
        long d = inserir(ana, "Adversário D", "Blitz", "Vitória");
        long e = inserir(ana, "Adversário E", "Blitz", "Empate");
        assertEquals(List.of(e, d), ids(partidas.listar(ana, "", "", "", 1, 2).itens()));
        assertEquals(List.of(c, b), ids(partidas.listar(ana, "", "", "", 2, 2).itens()));
        var ultima = partidas.listar(ana, "", "", "", 3, 2);
        assertEquals(5, ultima.total());
        assertEquals(List.of(a), ids(ultima.itens()));
        var alem = partidas.listar(ana, "", "", "", 4, 2);
        assertEquals(5, alem.total());
        assertTrue(alem.itens().isEmpty());
    }

    @Test
    void paginaExtremaNaoDeveEstourarOffsetInteiro() {
        inserir(ana, "Adversário", "Blitz", "Vitória");
        // Regressão: (pagina - 1) * porPagina transborda int e produz OFFSET negativo.
        var pagina = assertDoesNotThrow(() -> partidas.listar(ana, "", "", "", Integer.MAX_VALUE, 50));
        assertEquals(1, pagina.total());
        assertTrue(pagina.itens().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 50})
    void aceitaTamanhosMinimoEMaximoPermitidosPelaApi(int porPagina) {
        inserir(ana, "Adversário", "Blitz", "Vitória");
        var pagina = partidas.listar(ana, "", "", "", 1, porPagina);
        assertEquals(1, pagina.total());
        assertEquals(1, pagina.itens().size());
    }

    @Test
    void consultasSemPartidasRetornamColecoesEContagensVazias() {
        var pagina = partidas.listar(ana, "", "", "", 1, 10);
        assertEquals(0, pagina.total());
        assertTrue(pagina.itens().isEmpty());
        assertTrue(partidas.ultimas(ana, 5).isEmpty());
        assertTrue(partidas.contra(ana, "Ninguém").isEmpty());
        assertTrue(partidas.buscar(ana, Long.MAX_VALUE).isEmpty());
        assertEquals(new Contagem(0, 0, 0, 0), partidas.contagem(ana, ""));
    }

    @Test
    void contagemFiltraModoEConsideraTodosOsResultados() {
        inserir(ana, "Adversário", "Blitz", "Vitória");
        inserir(ana, "Adversário", "Blitz", "Empate");
        inserir(ana, "Adversário", "Rápido", "Derrota");
        inserir(outro, "Adversário", "Blitz", "Vitória");
        assertEquals(new Contagem(3, 1, 1, 1), partidas.contagem(ana, ""));
        assertEquals(new Contagem(2, 1, 1, 0), partidas.contagem(ana, "Blitz"));
        assertEquals(new Contagem(1, 0, 0, 1), partidas.contagem(ana, "Rápido"));
        assertEquals(new Contagem(0, 0, 0, 0), partidas.contagem(ana, "Clássico"));
    }

    @Test
    void retrospectoIgnoraCaixaMasExigeNomeCompletoIgual() {
        long primeira = inserir(ana, "Carlos Silva", "Blitz", "Vitória");
        long intermediaria = inserir(ana, "Carlos Silva Junior", "Blitz", "Derrota");
        long ultima = inserir(ana, "CARLOS SILVA", "Rápido", "Empate");
        assertEquals(List.of(ultima, primeira), ids(partidas.contra(ana, "carlos silva")));
        assertEquals(List.of(ultima, intermediaria), ids(partidas.ultimas(ana, 2)));
    }

    @Test
    void perfilAtualizadoPreservaIdentidadeCredencialEOutroJogador() {
        Usuario antes = usuarios.porId(ana).orElseThrow();
        Usuario outroAntes = usuarios.porId(outro).orElseThrow();
        usuarios.atualizarPerfil(ana, "Ána do Clube", "Rápido", "D'Ávila <xadrez> & amigos");
        Usuario depois = usuarios.porId(ana).orElseThrow();
        assertEquals(new Usuario(antes.id(), antes.nome(), antes.email(), "Ána do Clube", "Rápido", "D'Ávila <xadrez> & amigos"), depois);
        assertEquals(outroAntes, usuarios.porId(outro).orElseThrow());
        assertEquals("hash-de-teste", usuarios.credencial("ana@example.com").orElseThrow().hash());
    }

    @Test
    void registraEZeraFalhasSomenteDoUsuarioAlvo() {
        LocalDateTime ate = DATA.plusMinutes(15);
        usuarios.registrarFalha(ana, 3, ate);
        var bloqueada = usuarios.credencial("ana@example.com").orElseThrow();
        assertEquals(3, bloqueada.falhas());
        assertEquals(ate, bloqueada.bloqueadoAte());
        assertEquals(0, usuarios.credencial("outro@example.com").orElseThrow().falhas());
        usuarios.zerarFalhas(ana);
        var liberada = usuarios.credencial("ana@example.com").orElseThrow();
        assertEquals(0, liberada.falhas());
        assertNull(liberada.bloqueadoAte());
    }

    @Test
    void rankingIncluiJogadoresSemPartidasComContagemZerada() {
        List<LinhaRanking> ranking = usuarios.ranking();
        assertEquals(List.of("Ana", "Outro"), ranking.stream().map(LinhaRanking::nome).toList());
        assertTrue(ranking.stream().allMatch(l -> l.contagem().equals(new Contagem(0, 0, 0, 0))));
    }

    @Test
    void rankingPriorizaVitoriasDepoisAproveitamentoDepoisNome() {
        long bia = usuarios.inserir("Bia Souza", "bia@example.com", "hash", "Bia");
        long zoe = usuarios.inserir("Zoe Souza", "zoe@example.com", "hash", "Zoe");
        resultados(ana, 3, 0, 7); // Mais vitórias, mesmo com aproveitamento menor.
        resultados(outro, 2, 0, 2);
        resultados(bia, 2, 0, 0);
        resultados(zoe, 2, 0, 0);
        assertEquals(List.of("Ana", "Bia", "Zoe", "Outro"), usuarios.ranking().stream().map(LinhaRanking::nome).toList());
    }

    @Test
    void rankingDesempataComAproveitamentoRealAntesDoArredondamentoDeExibicao() {
        usuarios.atualizarPerfil(outro, "Zoe", "Blitz", "");
        resultados(ana, 10, 5, 11);  // 12,5 / 26 = 48,0769...%
        resultados(outro, 10, 6, 11); // 13 / 27 = 48,1481...%
        List<LinhaRanking> ranking = usuarios.ranking();
        // Ambas exibem 48,1%, mas Zoe tem aproveitamento maior antes do arredondamento.
        assertEquals(48.1, ranking.get(0).contagem().aproveitamento());
        assertEquals(48.1, ranking.get(1).contagem().aproveitamento());
        assertEquals(List.of("Zoe", "Ana"), ranking.stream().map(LinhaRanking::nome).toList());
    }

    private long inserir(long usuario, String adversario, String modo, String resultado) {
        return partidas.inserir(usuario, adversario, modo, resultado, DATA.plusMinutes(sequencia++));
    }

    private void resultados(long usuario, int vitorias, int empates, int derrotas) {
        for (int i = 0; i < vitorias; i++) inserir(usuario, "Adversário", "Blitz", "Vitória");
        for (int i = 0; i < empates; i++) inserir(usuario, "Adversário", "Blitz", "Empate");
        for (int i = 0; i < derrotas; i++) inserir(usuario, "Adversário", "Blitz", "Derrota");
    }

    private static List<Long> ids(List<Partida> lista) {
        return lista.stream().map(Partida::id).toList();
    }
}
