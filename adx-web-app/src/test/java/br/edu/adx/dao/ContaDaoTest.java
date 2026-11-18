package br.edu.adx.dao;

import static org.junit.jupiter.api.Assertions.*;

import br.edu.adx.dao.ContaDao.Estado;
import br.edu.adx.util.Formatos;
import br.edu.adx.util.Seguranca;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.ResourceLock;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ResourceLock("br.edu.adx.dao.Banco")
class ContaDaoTest {
    private static final String EMAIL = "ana@example.test";
    private static final String NOVO_EMAIL = "novo@example.test";
    private static final String OUTRO_EMAIL = "bia@example.test";
    private static final String SENHA_ANTIGA = "Senha antiga válida";
    private static final String SENHA_NOVA = "Senha nova válida";
    private static final Instant INICIO = Instant.parse("2026-10-26T20:00:00Z");

    private final UsuarioDao usuarios = new UsuarioDao();
    private final ContaDao contas = noInstante(0);
    private String bancoAnterior;
    private String senhaHash;
    private long ana;
    private long bia;

    @BeforeAll
    void iniciar() {
        bancoAnterior = System.getProperty("adx.db");
        System.setProperty("adx.db", "jdbc:h2:mem:adx-conta-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        Banco.iniciar();
        senhaHash = Seguranca.gerarHash(SENHA_ANTIGA);
    }

    @BeforeEach
    void preparar() throws SQLException {
        try (var c = Banco.conexao(); var st = c.createStatement()) {
            st.executeUpdate("DELETE FROM partidas");
            st.executeUpdate("DELETE FROM usuarios");
        }
        ana = usuarios.inserir("Ana Silva", EMAIL, senhaHash, "Ana");
        bia = usuarios.inserir("Bia Silva", OUTRO_EMAIL, senhaHash, "Bia");
    }

    @AfterAll
    void encerrar() throws SQLException {
        try (var c = Banco.conexao(); var st = c.createStatement()) {
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
    void codigoDeAlteracaoVaiAoEmailAtualEFicaAssociadoAContaEOperacao() {
        var pedido = contas.solicitarEmail(ana, 0, NOVO_EMAIL);
        assertEquals(Estado.PRONTO, pedido.estado());
        assertEquals(EMAIL, pedido.envio().destino());
        String codigo = pedido.envio().codigo();
        assertEquals(EMAIL, usuarios.porId(ana).orElseThrow().email());
        assertEquals(Estado.INVALIDO, contas.confirmarEmail(bia, 0, codigo));
        assertEquals(Estado.INVALIDO, contas.confirmarSenha(EMAIL, codigo, SENHA_NOVA));
        assertEquals(Estado.CONFIRMADO, contas.confirmarEmail(ana, 0, codigo));
        assertEquals(NOVO_EMAIL, usuarios.porId(ana).orElseThrow().email());
        assertEquals(Estado.INVALIDO, contas.confirmarEmail(ana, 0, codigo));
    }

    @Test
    void codigoVencidoNaoRedefineSenha() {
        String codigo = contas.solicitarSenha(EMAIL).orElseThrow().codigo();
        assertEquals(Estado.INVALIDO, noInstante(900).confirmarSenha(EMAIL, codigo, SENHA_NOVA));
        assertEquals(senhaHash, usuarios.credencial(EMAIL).orElseThrow().hash());
    }

    @Test
    void cincoConfirmacoesIncorretasInvalidamCodigo() {
        String codigo = contas.solicitarSenha(EMAIL).orElseThrow().codigo();
        String errado = (codigo.charAt(0) == '2' ? "3" : "2") + codigo.substring(1);
        for (int i = 0; i < 5; i++) {
            assertEquals(Estado.INVALIDO, contas.confirmarSenha(EMAIL, errado, SENHA_NOVA));
        }
        assertEquals(Estado.INVALIDO, contas.confirmarSenha(EMAIL, codigo, SENHA_NOVA));
        assertEquals(senhaHash, usuarios.credencial(EMAIL).orElseThrow().hash());
    }

    @Test
    void reenvioRespeitaIntervaloEInvalidaCodigoAnterior() {
        String codigo = contas.solicitarSenha(EMAIL).orElseThrow().codigo();
        assertTrue(noInstante(59).solicitarSenha(EMAIL).isEmpty());
        var posterior = noInstante(60);
        String novo = posterior.solicitarSenha(EMAIL).orElseThrow().codigo();
        assertEquals(Estado.INVALIDO, posterior.confirmarSenha(EMAIL, codigo, SENHA_NOVA));
        assertEquals(Estado.CONFIRMADO, posterior.confirmarSenha(EMAIL, novo, SENHA_NOVA));
    }

    @Test
    void recuperacaoAtualizaHashLimpaBloqueioERevogaSessoes() {
        var autenticacao = usuarios.autenticar(EMAIL, hash -> Seguranca.conferir(SENHA_ANTIGA, hash)).orElseThrow();
        assertTrue(autenticacao.autorizado());
        assertTrue(usuarios.sessaoAtual(ana, autenticacao.versaoCredencial()));
        String trocaEmail = contas.solicitarEmail(ana, autenticacao.versaoCredencial(), NOVO_EMAIL).envio().codigo();
        usuarios.registrarFalha(ana, 2, Formatos.agora().plusMinutes(15));
        var envio = contas.solicitarSenha(EMAIL).orElseThrow();
        assertEquals(EMAIL, envio.destino());
        assertEquals(Estado.CONFIRMADO, contas.confirmarSenha(EMAIL, envio.codigo(), SENHA_NOVA));

        var atual = usuarios.credencial(EMAIL).orElseThrow();
        assertTrue(Seguranca.conferir(SENHA_NOVA, atual.hash()));
        assertFalse(Seguranca.conferir(SENHA_ANTIGA, atual.hash()));
        assertEquals(0, atual.falhas());
        assertNull(atual.bloqueadoAte());
        assertFalse(usuarios.sessaoAtual(ana, autenticacao.versaoCredencial()));
        assertTrue(usuarios.sessaoAtual(bia, 0));
        assertEquals(Estado.SESSAO_EXPIRADA, contas.confirmarEmail(ana, 0, trocaEmail));
        assertEquals(Estado.INVALIDO, contas.confirmarEmail(ana, 1, trocaEmail));
        assertEquals(Estado.INVALIDO, contas.confirmarSenha(EMAIL, envio.codigo(), SENHA_NOVA));
    }

    @Test
    void emailOcupadoDepoisDaSolicitacaoNaoSubstituiContaExistente() {
        String codigo = contas.solicitarEmail(ana, 0, NOVO_EMAIL).envio().codigo();
        long novoUsuario = usuarios.inserir("Cris Silva", NOVO_EMAIL, senhaHash, "Cris");
        assertEquals(Estado.EMAIL_EM_USO, contas.confirmarEmail(ana, 0, codigo));
        assertEquals(EMAIL, usuarios.porId(ana).orElseThrow().email());
        assertEquals(NOVO_EMAIL, usuarios.porId(novoUsuario).orElseThrow().email());
        assertEquals(Estado.INVALIDO, contas.confirmarEmail(ana, 0, codigo));
    }

    @Test
    void trocaDeEmailDescartaRecuperacaoEnviadaAoEnderecoAnterior() {
        String recuperacao = contas.solicitarSenha(EMAIL).orElseThrow().codigo();
        String confirmacao = contas.solicitarEmail(ana, 0, NOVO_EMAIL).envio().codigo();
        assertEquals(Estado.CONFIRMADO, contas.confirmarEmail(ana, 0, confirmacao));
        assertEquals(Estado.INVALIDO, contas.confirmarSenha(EMAIL, recuperacao, SENHA_NOVA));
        assertEquals(Estado.INVALIDO, contas.confirmarSenha(NOVO_EMAIL, recuperacao, SENHA_NOVA));
        assertEquals(NOVO_EMAIL, contas.solicitarSenha(NOVO_EMAIL).orElseThrow().destino());
    }

    @Test
    void armazenaSomenteHashECodigoPodeSerUsadoUmaVezEmConcorrencia() throws Exception {
        String codigo = contas.solicitarSenha(EMAIL).orElseThrow().codigo();
        try (var c = Banco.conexao(); var st = c.createStatement();
             var rs = st.executeQuery("SELECT codigo_hash FROM codigos_conta")) {
            assertTrue(rs.next());
            assertEquals(32, rs.getBytes(1).length);
            assertNotEquals(codigo, new String(rs.getBytes(1), StandardCharsets.US_ASCII));
        }

        var sincronizar = new CyclicBarrier(2);
        var exec = Executors.newFixedThreadPool(2);
        try {
            var primeiro = exec.submit(() -> {
                sincronizar.await(5, TimeUnit.SECONDS);
                return contas.confirmarSenha(EMAIL, codigo, SENHA_NOVA);
            });
            var segundo = exec.submit(() -> {
                sincronizar.await(5, TimeUnit.SECONDS);
                return contas.confirmarSenha(EMAIL, codigo, SENHA_NOVA);
            });
            assertEquals(Set.of(Estado.CONFIRMADO, Estado.INVALIDO),
                    Set.copyOf(List.of(primeiro.get(10, TimeUnit.SECONDS), segundo.get(10, TimeUnit.SECONDS))));
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    void adicionaEstruturasDeContaSemApagarPerfilOuPartidasExistentes() throws SQLException {
        try (var c = Banco.conexao(); var st = c.createStatement()) {
            // Reproduz a estrutura legada no banco de memória desta suíte.
            st.execute("DROP TABLE codigos_conta");
            st.execute("ALTER TABLE usuarios DROP COLUMN versao_credencial");
            try (var ps = c.prepareStatement("""
                    INSERT INTO partidas (usuario_id, adversario, modo, resultado, data_hora)
                    VALUES (?, 'Adversário', 'Rápido', 'Empate', CURRENT_TIMESTAMP)
                    """)) {
                ps.setLong(1, ana);
                ps.executeUpdate();
            }
        }
        Banco.iniciar();
        Banco.iniciar();
        assertTrue(usuarios.sessaoAtual(ana, 0));
        assertEquals(EMAIL, usuarios.porId(ana).orElseThrow().email());
        assertTrue(contas.solicitarSenha(EMAIL).isPresent());
        try (var c = Banco.conexao(); var st = c.createStatement();
             var rs = st.executeQuery("SELECT COUNT(*) FROM partidas")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    private static ContaDao noInstante(long segundos) {
        return new ContaDao(Clock.fixed(INICIO.plusSeconds(segundos), ZoneOffset.UTC));
    }
}
