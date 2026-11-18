package br.edu.adx.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SegurancaTest {
    @Test
    void hashTemFormatoPbkdf2ComSalDe16BytesEDerivacaoDe32Bytes() {
        String senha = "Senha!123";
        String hash = Seguranca.gerarHash(senha);
        String[] partes = hash.split("\\$");
        assertEquals(4, partes.length);
        assertEquals("pbkdf2", partes[0]);
        assertEquals("120000", partes[1]);
        assertEquals(16, Base64.getDecoder().decode(partes[2]).length);
        assertEquals(32, Base64.getDecoder().decode(partes[3]).length);
        assertFalse(hash.contains(senha));
    }

    @Test
    void mesmaSenhaRecebeSaisDiferentesEMantemValidacao() {
        String primeiro = Seguranca.gerarHash("Senha!123");
        String segundo = Seguranca.gerarHash("Senha!123");
        assertNotEquals(primeiro.split("\\$")[2], segundo.split("\\$")[2]);
        assertNotEquals(primeiro, segundo);
        assertTrue(Seguranca.conferir("Senha!123", primeiro));
        assertTrue(Seguranca.conferir("Senha!123", segundo));
    }

    @Test
    void aceitaSenhaUnicodeEConfereExatamenteCaixaEEspacos() {
        String senha = "Árvore! 123 ç";
        String hash = Seguranca.gerarHash(senha);
        assertTrue(Seguranca.conferir(senha, hash));
        assertFalse(Seguranca.conferir("árvore! 123 ç", hash));
        assertFalse(Seguranca.conferir(senha + " ", hash));
        assertFalse(Seguranca.conferir("errada", hash));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"texto-puro", "pbkdf2", "pbkdf2$120000", "pbkdf2$120000$c2Fs", "pbkdf2$abc$c2Fs$aGFzaA==", "pbkdf2$0$c2Fs$aGFzaA==", "pbkdf2$-1$c2Fs$aGFzaA==", "pbkdf2$120000$***$aGFzaA==", "pbkdf2$120000$c2Fs$***", "pbkdf2$120000$$aGFzaA=="})
    void hashMalformadoRetornaFalsoSemLancarExcecao(String hash) {
        assertFalse(assertDoesNotThrow(() -> Seguranca.conferir("Senha!123", hash)));
    }

    @Test
    void hashComDerivacaoCorrompidaNaoAutentica() {
        String hash = Seguranca.gerarHash("Senha!123");
        String[] partes = hash.split("\\$");
        byte[] bytes = Base64.getDecoder().decode(partes[3]);
        bytes[0] ^= 1;
        partes[3] = Base64.getEncoder().encodeToString(bytes);
        assertFalse(Seguranca.conferir("Senha!123", String.join("$", partes)));
    }

    @Test
    void hashFalsoNaoCorrespondeASenhaDoUsuario() {
        assertFalse(Seguranca.conferir("Senha!123", Seguranca.HASH_FALSO));
    }
}
