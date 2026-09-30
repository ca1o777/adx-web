package br.com.adx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

/** RN003: a sessão dura até 30 dias e termina no logout. */
class SessaoTest extends TesteDeIntegracao {

    @Test
    void devolveOUsuarioLogado() throws Exception {
        Cookie sessao = cadastrarEEntrar();

        mvc.perform(get("/api/auth/sessao").cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeCompleto").value(NOME))
                .andExpect(jsonPath("$.nomeExibicao").value("Caio Soares"))
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void semCookieOuComCookieInventadoResponde401() throws Exception {
        cadastrarEEntrar();

        mvc.perform(get("/api/auth/sessao"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("Faça login para continuar."));
        mvc.perform(get("/api/auth/sessao").cookie(new Cookie(COOKIE, "token-inventado")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessaoValeAte30Dias() throws Exception {
        Cookie sessao = cadastrarEEntrar();

        relogio.avancar(Duration.ofDays(30).minusSeconds(1));
        mvc.perform(get("/api/auth/sessao").cookie(sessao)).andExpect(status().isOk());

        relogio.avancar(Duration.ofSeconds(1));
        mvc.perform(get("/api/auth/sessao").cookie(sessao)).andExpect(status().isUnauthorized());
        assertThat(sessoesNoBanco()).isZero();
    }

    @Test
    void logoutEncerraASessaoNoServidorEApagaOCookie() throws Exception {
        Cookie sessao = cadastrarEEntrar();

        Cookie apagado = mvc.perform(post("/api/auth/logout").cookie(sessao))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie(COOKIE);

        assertThat(apagado.getMaxAge()).isZero();
        assertThat(apagado.getValue()).isEmpty();
        assertThat(sessoesNoBanco()).isZero();
        mvc.perform(get("/api/auth/sessao").cookie(sessao)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutSemSessaoTambemResponde204() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    void entrarDeNovoNoMesmoNavegadorTrocaASessao() throws Exception {
        Cookie primeira = cadastrarEEntrar();

        String corpo = new ObjectMapper().writeValueAsString(Map.of("email", EMAIL, "senha", SENHA));
        Cookie segunda = mvc.perform(post("/api/auth/login").cookie(primeira)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(COOKIE);

        assertThat(segunda.getValue()).isNotEqualTo(primeira.getValue());
        mvc.perform(get("/api/auth/sessao").cookie(primeira)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/sessao").cookie(segunda)).andExpect(status().isOk());
    }

    private int sessoesNoBanco() {
        return banco.queryForObject("SELECT COUNT(*) FROM sessao", Integer.class);
    }
}
