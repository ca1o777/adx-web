package br.com.adx.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.adx.dto.DadosCadastro;
import br.com.adx.dto.DadosUsuario;
import br.com.adx.erro.RegraNegocioException;
import br.com.adx.model.Usuario;
import br.com.adx.repository.UsuarioRepository;

@Service
public class AutenticacaoService {

    /** O BCrypt só considera os primeiros 72 bytes da senha. */
    private static final int LIMITE_BYTES_DA_SENHA = 72;
    private static final int TAMANHO_MAXIMO_NOME_EXIBICAO = 40;

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final Clock relogio;

    public AutenticacaoService(UsuarioRepository usuarios, PasswordEncoder codificador, Clock relogio) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.relogio = relogio;
    }

    /** RN001: cadastra o usuário com e-mail único e a senha guardada com BCrypt. */
    @Transactional
    public DadosUsuario cadastrar(DadosCadastro dados) {
        if (dados.senha().getBytes(StandardCharsets.UTF_8).length > LIMITE_BYTES_DA_SENHA) {
            throw RegraNegocioException.doCampo(HttpStatus.BAD_REQUEST, "senha", DadosCadastro.SENHA_INVALIDA);
        }
        String email = normalizarEmail(dados.email());
        if (usuarios.existsByEmail(email)) {
            throw emailJaCadastrado();
        }
        Usuario usuario = new Usuario(dados.nome(), nomeDeExibicaoInicial(dados.nome()), email,
                codificador.encode(dados.senha()), relogio.instant());
        try {
            usuarios.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            throw emailJaCadastrado(); // outro cadastro com o mesmo e-mail foi gravado antes deste
        }
        return DadosUsuario.de(usuario);
    }

    /** E-mails são comparados sem diferenciar maiúsculas: "Caio@X.com" e "caio@x.com" são o mesmo. */
    static String normalizarEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /** O nome de exibição começa como primeiro + último nome ("Caio Fernandes Soares" vira "Caio Soares"). */
    static String nomeDeExibicaoInicial(String nomeCompleto) {
        String[] partes = nomeCompleto.split(" ");
        String nome = partes[0] + " " + partes[partes.length - 1];
        if (nome.length() <= TAMANHO_MAXIMO_NOME_EXIBICAO) {
            return nome;
        }
        return partes[0].substring(0, Math.min(partes[0].length(), TAMANHO_MAXIMO_NOME_EXIBICAO));
    }

    private static RegraNegocioException emailJaCadastrado() {
        return RegraNegocioException.doCampo(HttpStatus.CONFLICT, "email", "Este e-mail já está cadastrado.");
    }
}
