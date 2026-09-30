package br.com.adx;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

/**
 * Base dos testes de integração: sobe a aplicação inteira com um banco SQLite próprio
 * (target/adx-teste.db, recriado a cada execução) e o {@link RelogioDeTeste}.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/adx-teste.db")
@AutoConfigureMockMvc
@Import(RelogioDeTeste.Configuracao.class)
public abstract class TesteDeIntegracao {

    protected static final String NOME = "Caio Fernandes Soares";
    protected static final String EMAIL = "caio@exemplo.com";
    protected static final String SENHA = "adx12345";
    protected static final String COOKIE = "ADX_SESSAO";

    static {
        try {
            Files.deleteIfExists(Path.of("target", "adx-teste.db"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcTemplate banco;
    @Autowired
    protected RelogioDeTeste relogio;
    @Autowired
    private ObjectMapper json;

    @BeforeEach
    void limparBancoERelogio() {
        banco.update("DELETE FROM sessao");
        banco.update("DELETE FROM usuario");
        relogio.reiniciar();
    }

    protected ResultActions cadastrar(String nome, String email, String senha) throws Exception {
        return enviar("/api/auth/cadastro", Map.of("nome", nome, "email", email, "senha", senha));
    }

    protected ResultActions entrar(String email, String senha) throws Exception {
        return enviar("/api/auth/login", Map.of("email", email, "senha", senha));
    }

    /** Cadastra o usuário padrão, faz login e devolve o cookie de sessão. */
    protected Cookie cadastrarEEntrar() throws Exception {
        cadastrar(NOME, EMAIL, SENHA).andExpect(status().isCreated());
        return entrar(EMAIL, SENHA).andExpect(status().isOk()).andReturn().getResponse().getCookie(COOKIE);
    }

    protected ResultActions enviar(String rota, Map<String, String> corpo) throws Exception {
        return mvc.perform(post(rota).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo)));
    }
}
