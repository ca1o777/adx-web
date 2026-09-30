package br.com.adx.seguranca;

import java.io.IOException;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import br.com.adx.erro.RegraNegocioException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * RN003: sem login, só as telas de login e cadastro ficam acessíveis. Este interceptor confere
 * o cookie de sessão e, se ele for válido, guarda o id do usuário na requisição.
 */
public class ExigeLoginInterceptor implements HandlerInterceptor {

    /** O que responder a quem não está logado. */
    public enum SemLogin {
        /** Rotas da API: 401 em JSON. */
        RESPONDER_401,
        /** Páginas: redireciona para a tela de login. */
        IR_PARA_O_LOGIN
    }

    private final SessaoService sessoes;
    private final SemLogin semLogin;

    public ExigeLoginInterceptor(SessaoService sessoes, SemLogin semLogin) {
        this.sessoes = sessoes;
        this.semLogin = semLogin;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        Optional<Long> usuarioId = CookieDeSessao.ler(request).flatMap(sessoes::usuarioDoToken);
        if (usuarioId.isPresent()) {
            request.setAttribute(UsuarioLogado.ATRIBUTO, usuarioId.get());
            return true;
        }
        if (semLogin == SemLogin.RESPONDER_401) {
            throw loginNecessario();
        }
        response.sendRedirect(request.getContextPath() + "/index.html");
        return false;
    }

    static RegraNegocioException loginNecessario() {
        return new RegraNegocioException(HttpStatus.UNAUTHORIZED, "Faça login para continuar.");
    }
}
