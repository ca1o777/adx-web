package br.edu.adx.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CorreioTest {
    private static Map<String, String> ambienteLocal() {
        return new HashMap<>(Map.of("ADX_SMTP_MODO", "local", "ADX_SMTP_HOST", "127.0.0.1",
                "ADX_SMTP_REMETENTE", "adx@example.test"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1:8025/", "http://[::1]", "http://[::1]:65535/"})
    void disponibilizaCaixaLocalConfigurada(String url) {
        var ambiente = ambienteLocal();
        ambiente.put("ADX_SMTP_CAIXA_LOCAL_URL", url);
        assertEquals(Optional.of(url), new Correio(ambiente).caixaDeEntrada());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://127.0.0.1:8025/", "http://mail.example.test/", "http://127.0.0.1:0/",
            "http://127.0.0.1:65536/", "http://127.0.0.1@outro.example/", "http://usuario@127.0.0.1/",
            "http://127.0.0.1/caminho", "http://127.0.0.1/?destino=x", "http://127.0.0.1/#fragmento"})
    void ignoraLinkInvalidoSemInterromperSmtp(String url) {
        var ambiente = ambienteLocal();
        ambiente.put("ADX_SMTP_CAIXA_LOCAL_URL", url);
        var correio = new Correio(ambiente);
        assertTrue(correio.configurado());
        assertTrue(correio.caixaDeEntrada().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ssl", "starttls"})
    void ocultaCaixaEmModosDeEntregaExterna(String modo) {
        var ambiente = ambienteLocal();
        ambiente.put("ADX_SMTP_MODO", modo);
        ambiente.put("ADX_SMTP_CAIXA_LOCAL_URL", "http://127.0.0.1:8025/");
        assertTrue(new Correio(ambiente).caixaDeEntrada().isEmpty());
    }

    @Test
    void ocultaCaixaQuandoFaltaConfiguracao() {
        var ambiente = ambienteLocal();
        assertTrue(new Correio(ambiente).caixaDeEntrada().isEmpty());
        ambiente.put("ADX_SMTP_CAIXA_LOCAL_URL", "http://127.0.0.1:8025/");
        ambiente.remove("ADX_SMTP_HOST");
        assertTrue(new Correio(ambiente).caixaDeEntrada().isEmpty());
    }
}
