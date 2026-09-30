package br.com.adx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** RN001: e-mail único e senha guardada com BCrypt. */
class CadastroTest extends TesteDeIntegracao {

    @Test
    void cadastraEGuardaASenhaComBCrypt() throws Exception {
        cadastrar("  Caio   Fernandes Soares ", " Caio@Exemplo.com ", SENHA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nomeCompleto").value("Caio Fernandes Soares"))
                .andExpect(jsonPath("$.nomeExibicao").value("Caio Soares"))
                .andExpect(jsonPath("$.email").value("caio@exemplo.com"))
                .andExpect(content().string(not(containsString("senha"))));

        String hash = banco.queryForObject("SELECT senha_hash FROM usuario WHERE email = ?", String.class, EMAIL);
        assertThat(hash).startsWith("$2").isNotEqualTo(SENHA);
        assertThat(new BCryptPasswordEncoder().matches(SENHA, hash)).isTrue();
    }

    @Test
    void recusaEmailJaCadastradoSemDiferenciarMaiusculas() throws Exception {
        cadastrar(NOME, EMAIL, SENHA).andExpect(status().isCreated());

        cadastrar("Outra Pessoa", "CAIO@exemplo.com", "outrasenha")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campos.email").value("Este e-mail já está cadastrado."));

        assertThat(banco.queryForObject("SELECT COUNT(*) FROM usuario", Integer.class)).isEqualTo(1);
    }

    @Test
    void recusaDadosInvalidosIndicandoCadaCampo() throws Exception {
        cadastrar("Caio", "caio@x", "1234567")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nome").value("Informe nome e sobrenome."))
                .andExpect(jsonPath("$.campos.email").value("Informe um e-mail válido."))
                .andExpect(jsonPath("$.campos.senha").value("A senha precisa ter entre 8 e 72 caracteres."));
    }

    @Test
    void recusaSenhaAcimaDoLimiteDoBCrypt() throws Exception {
        String senhaDe74Bytes = "ç".repeat(37);

        cadastrar(NOME, EMAIL, senhaDe74Bytes)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.senha").exists());
    }

    @Test
    void respondeNoFormatoDeErroQuandoOJsonEstaQuebrado() throws Exception {
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("{\"nome\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());
    }
}
