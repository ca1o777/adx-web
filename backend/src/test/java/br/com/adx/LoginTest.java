package br.com.adx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockCookie;

/** RN003: login, e bloqueio de 15 minutos depois de 3 senhas erradas seguidas. */
class LoginTest extends TesteDeIntegracao {

    private static final String SENHA_ERRADA = "senha-errada";

    @BeforeEach
    void cadastrarUsuario() throws Exception {
        cadastrar(NOME, EMAIL, SENHA).andExpect(status().isCreated());
    }

    @Test
    void loginCertoDevolveCookieDe30DiasEGuardaSoOHashDoToken() throws Exception {
        MockCookie cookie = (MockCookie) entrar("CAIO@exemplo.com", SENHA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeExibicao").value("Caio Soares"))
                .andReturn().getResponse().getCookie(COOKIE);

        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo((int) Duration.ofDays(30).toSeconds());

        String gravado = banco.queryForObject("SELECT token_hash FROM sessao", String.class);
        assertThat(gravado).isEqualTo(sha256(cookie.getValue())).isNotEqualTo(cookie.getValue());
    }

    @Test
    void senhaErradaEEmailInexistenteRecebemAMesmaResposta() throws Exception {
        entrar(EMAIL, SENHA_ERRADA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("E-mail ou senha incorretos."))
                .andExpect(jsonPath("$.campos").doesNotExist());
        entrar("ninguem@exemplo.com", SENHA_ERRADA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("E-mail ou senha incorretos."))
                .andExpect(jsonPath("$.campos").doesNotExist());
    }

    @Test
    void tresSenhasErradasSeguidasBloqueiamPor15Minutos() throws Exception {
        entrar(EMAIL, SENHA_ERRADA).andExpect(status().isUnauthorized());
        entrar(EMAIL, SENHA_ERRADA).andExpect(status().isUnauthorized());
        entrar(EMAIL, SENHA_ERRADA).andExpect(status().isLocked());

        // durante o bloqueio, nem a senha certa entra
        entrar(EMAIL, SENHA).andExpect(status().isLocked());
        relogio.avancar(Duration.ofMinutes(15).minusSeconds(1));
        entrar(EMAIL, SENHA)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.erro").value(
                        "Acesso bloqueado por 3 senhas erradas seguidas. Tente novamente em 1 minuto."));

        relogio.avancar(Duration.ofSeconds(1));
        entrar(EMAIL, SENHA).andExpect(status().isOk());
    }

    @Test
    void loginCertoZeraAContagemDeSenhasErradas() throws Exception {
        entrar(EMAIL, SENHA_ERRADA).andExpect(status().isUnauthorized());
        entrar(EMAIL, SENHA_ERRADA).andExpect(status().isUnauthorized());
        entrar(EMAIL, SENHA).andExpect(status().isOk());

        entrar(EMAIL, SENHA_ERRADA).andExpect(status().isUnauthorized());
        entrar(EMAIL, SENHA_ERRADA).andExpect(status().isUnauthorized());
        entrar(EMAIL, SENHA).andExpect(status().isOk());
    }

    @Test
    void depoisDoBloqueioSaoTresTentativasNovas() throws Exception {
        for (int i = 0; i < 3; i++) {
            entrar(EMAIL, SENHA_ERRADA);
        }
        relogio.avancar(Duration.ofMinutes(15));

        entrar(EMAIL, SENHA_ERRADA).andExpect(status().isUnauthorized());
        entrar(EMAIL, SENHA_ERRADA).andExpect(status().isUnauthorized());
        entrar(EMAIL, SENHA_ERRADA).andExpect(status().isLocked());
    }

    @Test
    void senhaErradaCurtaTambemContaComoTentativa() throws Exception {
        entrar(EMAIL, "x").andExpect(status().isUnauthorized());
        entrar(EMAIL, "x").andExpect(status().isUnauthorized());
        entrar(EMAIL, "x").andExpect(status().isLocked());
    }

    @Test
    void senhaAcimaDoLimiteDoBCryptEApenasUmaSenhaErrada() throws Exception {
        entrar(EMAIL, "ç".repeat(40)).andExpect(status().isUnauthorized());
    }

    @Test
    void camposEmBrancoSaoRecusadosSemContarTentativa() throws Exception {
        for (int i = 0; i < 4; i++) {
            entrar(EMAIL, "")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.senha").value("Informe sua senha."));
        }
        entrar(EMAIL, SENHA).andExpect(status().isOk());
    }

    private static String sha256(String texto) throws Exception {
        byte[] resumo = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(resumo);
    }
}
