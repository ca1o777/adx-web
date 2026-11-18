package br.edu.adx.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RegrasTest {
    @ParameterizedTest
    @ValueSource(strings = {"jogador@example.com", "JOAO@EXAMPLE.COM", "ana.silva+clube@example.com.br"})
    void aceitaEmailsValidos(String email) {
        assertTrue(Regras.emailValido(email));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"sem-arroba.com", "a@@example.com", "a@localhost", "a@b.c", " a@example.com", "a@example.com ", "a b@example.com", "a@exam\tple.com"})
    void rejeitaEmailsInvalidos(String email) {
        assertFalse(Regras.emailValido(email));
    }

    @Test
    void emailTemLimiteExatoDe120Caracteres() {
        assertTrue(Regras.emailValido("a".repeat(108) + "@example.com"));
        assertFalse(Regras.emailValido("a".repeat(109) + "@example.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"João da Silva", "Ana-Maria D'Ávila", "João\tSilva", "João   Silva"})
    void cadastroAceitaNomeComAcentosESeparadores(String nome) {
        // Os servlets normalizam espaços externos antes de chamar as regras.
        assertTrue(Regras.validarCadastro(nome, "ana@example.com", "Senha123", "Senha123").isEmpty());
    }

    @Test
    void cadastroAceitaNomeCom100CaracteresESenhaCom8Ou72() {
        String nome = "A".repeat(98) + " B";
        for (int tamanho : new int[] {8, 72}) {
            String senha = "s".repeat(tamanho);
            assertTrue(Regras.validarCadastro(nome, "ana@example.com", senha, senha).isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "João"})
    void cadastroExigeNomeESobrenome(String nome) {
        assertEquals(Set.of("nome"), Regras.validarCadastro(nome, "ana@example.com", "Senha123", "Senha123").keySet());
    }

    @Test
    void cadastroRejeitaNomeCom101Caracteres() {
        assertEquals(Set.of("nome"), Regras.validarCadastro("A".repeat(99) + " B", "ana@example.com", "Senha123", "Senha123").keySet());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7, 73})
    void cadastroRejeitaSenhaForaDosLimites(int tamanho) {
        String senha = "s".repeat(tamanho);
        var erros = Regras.validarCadastro("Ana Silva", "ana@example.com", senha, senha);
        assertTrue(erros.containsKey("senha"));
        assertEquals(tamanho == 0, erros.containsKey("confirmar"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "senha123", "Senha123 "})
    void cadastroExigeConfirmacaoIdentica(String confirmar) {
        assertEquals(Set.of("confirmar"), Regras.validarCadastro("Ana Silva", "ana@example.com", "Senha123", confirmar).keySet());
    }

    @Test
    void cadastroRetornaTodosOsCamposInvalidos() {
        assertEquals(Set.of("nome", "email", "senha", "confirmar"), Regras.validarCadastro("Ana", "invalido", "123", "456").keySet());
    }

    @ParameterizedTest
    @CsvSource({"Clássico,Vitória", "Rápido,Empate", "Blitz,Derrota"})
    void partidaAceitaModosEResultadosDefinidos(String modo, String resultado) {
        assertTrue(Regras.validarPartida("Álvaro", modo, resultado).isEmpty());
    }

    @Test
    void partidaRespeitaLimitesDe2A60CaracteresDoAdversario() {
        for (int tamanho : new int[] {2, 60}) {
            assertTrue(Regras.validarPartida("a".repeat(tamanho), "Blitz", "Vitória").isEmpty());
        }
        for (int tamanho : new int[] {0, 1, 61}) {
            assertEquals(Set.of("adversario"), Regras.validarPartida("a".repeat(tamanho), "Blitz", "Vitória").keySet());
        }
    }

    @Test
    void partidaRejeitaValoresForaDasListasInclusiveVariacoesDeCaixa() {
        assertEquals(Set.of("adversario", "modo", "resultado"), Regras.validarPartida("", "blitz", "vitoria").keySet());
    }

    @Test
    void perfilAceitaLimitesExatosENomeUnicode() {
        assertTrue(Regras.validarPerfil("Ál", "Rápido", "").isEmpty());
        assertTrue(Regras.validarPerfil("Á".repeat(40), "Clássico", "ç".repeat(300)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 41})
    void perfilRejeitaNomeForaDosLimites(int tamanho) {
        assertEquals(Set.of("nomeExibicao"), Regras.validarPerfil("a".repeat(tamanho), "Blitz", "").keySet());
    }

    @Test
    void perfilRejeitaModoDesconhecidoESobreCom301Caracteres() {
        assertEquals(Set.of("modoPreferido", "sobre"), Regras.validarPerfil("Ana", "Bullet", "s".repeat(301)).keySet());
    }

    @ParameterizedTest
    @CsvSource({"'  João   da Silva  ','João da'", "'Ana','Ana'", "'Ana\tSilva Costa','Ana Silva'"})
    void nomePadraoUsaDuasPrimeirasPartesERemoveEspacosExternos(String completo, String esperado) {
        assertEquals(esperado, Regras.nomeExibicaoPadrao(completo));
    }

    @Test
    void nomePadraoNaoUltrapassa40Caracteres() {
        assertEquals("A".repeat(40), Regras.nomeExibicaoPadrao("A".repeat(50) + " Silva"));
    }
}
