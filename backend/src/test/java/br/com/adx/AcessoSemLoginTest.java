package br.com.adx;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.servlet.http.Cookie;

/** RN003: sem login, só as telas de login e cadastro ficam acessíveis. */
class AcessoSemLoginTest extends TesteDeIntegracao {

    @ParameterizedTest
    @ValueSource(strings = { "/painel.html", "/registrar-partida.html", "/historico.html", "/partida.html",
            "/ranking.html", "/perfil.html" })
    void paginaInternaSemLoginLevaParaATelaDeLogin(String pagina) throws Exception {
        mvc.perform(get(pagina))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/index.html"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/index.html", "/cadastro.html", "/assets/css/style.css", "/assets/js/app.js" })
    void loginCadastroEArquivosDasTelasAbremSemLogin(String caminho) throws Exception {
        mvc.perform(get(caminho)).andExpect(status().isOk());
    }

    @Test
    void raizLevaParaATelaDeLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().isFound()).andExpect(redirectedUrl("/index.html"));
    }

    @Test
    void comLoginAsPaginasInternasAbrem() throws Exception {
        Cookie sessao = cadastrarEEntrar();

        mvc.perform(get("/painel.html").cookie(sessao)).andExpect(status().isOk());
        mvc.perform(get("/perfil.html").cookie(sessao)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = { "/index.html", "/cadastro.html" })
    void quemJaEstaLogadoVaiDoLoginParaOPainel(String pagina) throws Exception {
        Cookie sessao = cadastrarEEntrar();

        mvc.perform(get(pagina).cookie(sessao))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/painel.html"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/README.md", "/CLAUDE.md", "/backend/pom.xml", "/backend/adx.db", "/.git/config",
            "/.gitignore" })
    void arquivosDoRepositorioNaoSaoServidosNemComLogin(String caminho) throws Exception {
        Cookie sessao = cadastrarEEntrar();

        mvc.perform(get(caminho).cookie(sessao)).andExpect(status().isNotFound());
    }
}
