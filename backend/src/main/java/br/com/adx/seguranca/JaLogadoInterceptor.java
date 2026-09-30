package br.com.adx.seguranca;

import java.io.IOException;

import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Quem já está logado e abre a tela de login ou de cadastro vai direto para o painel. */
public class JaLogadoInterceptor implements HandlerInterceptor {

    private final SessaoService sessoes;

    public JaLogadoInterceptor(SessaoService sessoes) {
        this.sessoes = sessoes;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        boolean logado = CookieDeSessao.ler(request).flatMap(sessoes::usuarioDoToken).isPresent();
        if (logado) {
            response.sendRedirect(request.getContextPath() + "/painel.html");
            return false;
        }
        return true;
    }
}
