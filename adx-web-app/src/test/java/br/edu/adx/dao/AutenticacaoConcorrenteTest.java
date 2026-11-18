package br.edu.adx.dao;

import static org.junit.jupiter.api.Assertions.*;

import br.edu.adx.dao.UsuarioDao.Autenticacao;
import br.edu.adx.util.Formatos;
import br.edu.adx.util.Regras;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Exercita transações e bloqueios de linha com conexões H2 reais e requisições simultâneas. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("br.edu.adx.dao.Banco")
class AutenticacaoConcorrenteTest {
    private final UsuarioDao usuarios = new UsuarioDao();
    private String bancoAnterior;
    private long ana;

    @BeforeAll
    void iniciarBancoIsolado() {
        bancoAnterior = System.getProperty("adx.db");
        System.setProperty("adx.db", "jdbc:h2:mem:adx-auth-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        Banco.iniciar();
    }

    @BeforeEach
    void limparDados() throws SQLException {
        try (Connection c = Banco.conexao(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM partidas");
            st.executeUpdate("DELETE FROM usuarios");
        }
        ana = usuarios.inserir("Ana Silva", "ana@example.com", "hash-de-teste", "Ana");
        usuarios.inserir("Bia Silva", "bia@example.com", "outro-hash", "Bia");
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
    void tresSenhasIncorretasConcorrentesBloqueiamSemPerderIncrementos() throws Exception {
        LocalDateTime antes = Formatos.agora();
        List<Autenticacao> resultados = falhasSimultaneas(3, new AtomicInteger());
        assertEquals(2, resultados.stream().filter(r -> r.bloqueadoAte() == null).count());
        assertEquals(1, resultados.stream().filter(r -> r.bloqueadoAte() != null).count());
        assertTrue(resultados.stream().noneMatch(Autenticacao::autorizado));
        var credencial = usuarios.credencial("ana@example.com").orElseThrow();
        assertEquals(0, credencial.falhas());
        assertFalse(credencial.bloqueadoAte().isBefore(antes.plusMinutes(Regras.BLOQUEIO_MINUTOS)));
        assertFalse(credencial.bloqueadoAte().isAfter(Formatos.agora().plusMinutes(Regras.BLOQUEIO_MINUTOS)));
    }

    @Test
    void rajadaDeFalhasNaoProrrogaBloqueioNemConfereSenhaAposTerceiraFalha() throws Exception {
        AtomicInteger verificacoes = new AtomicInteger();
        List<Autenticacao> resultados = falhasSimultaneas(8, verificacoes);
        assertEquals(3, verificacoes.get());
        assertEquals(6, resultados.stream().filter(r -> r.bloqueadoAte() != null).count());
        assertEquals(1, resultados.stream().map(Autenticacao::bloqueadoAte).filter(t -> t != null).distinct().count());
    }

    @Test
    void emailAusenteNaoInvocaVerificadorNemCriaUsuario() {
        assertTrue(usuarios.autenticar("ausente@example.com", hash -> {
            fail("O servlet executa o hash falso quando a conta não existe.");
            return false;
        }).isEmpty());
        assertEquals(2, usuarios.contar());
    }

    @Test
    void bloqueioAtivoNaoEContornadoPorSenhaCorreta() {
        LocalDateTime ate = Formatos.agora().plusMinutes(5);
        usuarios.registrarFalha(ana, 0, ate);
        Autenticacao resultado = usuarios.autenticar("ana@example.com", hash -> {
            fail("Uma conta bloqueada não deve conferir a senha nem limpar o bloqueio.");
            return true;
        }).orElseThrow();
        assertFalse(resultado.autorizado());
        assertEquals(ate, resultado.bloqueadoAte());
        assertEquals(ate, usuarios.credencial("ana@example.com").orElseThrow().bloqueadoAte());
    }

    @Test
    void bloqueioExpiradoPermiteNovoCicloDeTentativas() {
        usuarios.registrarFalha(ana, 0, Formatos.agora().minusSeconds(1));
        Autenticacao resultado = usuarios.autenticar("ana@example.com", hash -> false).orElseThrow();
        assertFalse(resultado.autorizado());
        assertNull(resultado.bloqueadoAte());
        var credencial = usuarios.credencial("ana@example.com").orElseThrow();
        assertEquals(1, credencial.falhas());
        assertNull(credencial.bloqueadoAte());
    }

    @Test
    void sucessoZeraDuasFalhasEIniciaNovoCiclo() {
        usuarios.registrarFalha(ana, 2, null);
        Autenticacao resultado = usuarios.autenticar("ana@example.com", "hash-de-teste"::equals).orElseThrow();
        assertTrue(resultado.autorizado());
        assertEquals(ana, resultado.usuarioId());
        assertEquals(0, usuarios.credencial("ana@example.com").orElseThrow().falhas());
        usuarios.autenticar("ana@example.com", hash -> false);
        assertEquals(1, usuarios.credencial("ana@example.com").orElseThrow().falhas());
    }

    @Test
    void falhaDoVerificadorDesfazTransacaoELiberaALinha() {
        usuarios.registrarFalha(ana, 2, null);
        IllegalStateException original = new IllegalStateException("Falha simulada do verificador");
        assertSame(original, assertThrows(IllegalStateException.class,
                () -> usuarios.autenticar("ana@example.com", hash -> { throw original; })));
        assertEquals(2, usuarios.credencial("ana@example.com").orElseThrow().falhas());
        assertTrue(usuarios.autenticar("ana@example.com", hash -> true).orElseThrow().autorizado());
    }

    @Test
    void verificacaoLentaDeUmaContaNaoBloqueiaOutraConta() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch entrou = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        try {
            Future<Autenticacao> primeira = executor.submit(() -> usuarios.autenticar("ana@example.com", hash -> {
                entrou.countDown();
                aguardar(liberar);
                return false;
            }).orElseThrow());
            assertTrue(entrou.await(5, TimeUnit.SECONDS));
            Future<Autenticacao> outra = executor.submit(() ->
                    usuarios.autenticar("bia@example.com", hash -> true).orElseThrow());
            assertTrue(outra.get(5, TimeUnit.SECONDS).autorizado());
            liberar.countDown();
            assertFalse(primeira.get(5, TimeUnit.SECONDS).autorizado());
        } finally {
            liberar.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void sucessoEsperaFalhaEmAndamentoEZeraContagemAtualizada() throws Exception {
        conferirOrdemDeTentativas(false, true, 0);
    }

    @Test
    void falhaEsperaSucessoEmAndamentoENaoEApagadaPorEle() throws Exception {
        conferirOrdemDeTentativas(true, false, 1);
    }

    private void conferirOrdemDeTentativas(boolean primeiraCorreta, boolean segundaCorreta, int falhas) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch primeiraEntrou = new CountDownLatch(1);
        CountDownLatch liberarPrimeira = new CountDownLatch(1);
        CountDownLatch segundaIniciada = new CountDownLatch(1);
        CountDownLatch segundaEntrou = new CountDownLatch(1);
        try {
            Future<Autenticacao> primeira = executor.submit(() -> usuarios.autenticar("ana@example.com", hash -> {
                primeiraEntrou.countDown();
                aguardar(liberarPrimeira);
                return primeiraCorreta;
            }).orElseThrow());
            assertTrue(primeiraEntrou.await(5, TimeUnit.SECONDS));
            Future<Autenticacao> segunda = executor.submit(() -> {
                segundaIniciada.countDown();
                return usuarios.autenticar("ana@example.com", hash -> {
                    segundaEntrou.countDown();
                    return segundaCorreta;
                }).orElseThrow();
            });
            assertTrue(segundaIniciada.await(5, TimeUnit.SECONDS));
            assertFalse(segundaEntrou.await(100, TimeUnit.MILLISECONDS), "A mesma conta deve aguardar a primeira transação.");
            liberarPrimeira.countDown();
            assertEquals(primeiraCorreta, primeira.get(5, TimeUnit.SECONDS).autorizado());
            assertEquals(segundaCorreta, segunda.get(5, TimeUnit.SECONDS).autorizado());
            assertEquals(falhas, usuarios.credencial("ana@example.com").orElseThrow().falhas());
        } finally {
            liberarPrimeira.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private List<Autenticacao> falhasSimultaneas(int quantidade, AtomicInteger verificacoes) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(quantidade);
        CyclicBarrier disparo = new CyclicBarrier(quantidade);
        try {
            List<Future<Autenticacao>> pendentes = new ArrayList<>();
            for (int i = 0; i < quantidade; i++) {
                pendentes.add(executor.submit(() -> {
                    disparo.await(5, TimeUnit.SECONDS);
                    return usuarios.autenticar("ana@example.com", hash -> {
                        verificacoes.incrementAndGet();
                        return false;
                    }).orElseThrow();
                }));
            }
            List<Autenticacao> resultados = new ArrayList<>();
            for (Future<Autenticacao> pendente : pendentes) {
                resultados.add(pendente.get(10, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void aguardar(CountDownLatch sinal) {
        try {
            if (!sinal.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Tempo esgotado aguardando sincronização do teste.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
