package br.edu.adx.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Set;

/**
 * RN003: usuários não autenticados têm acesso apenas às telas de login e cadastro.
 * Também adiciona cabeçalhos de segurança a todas as respostas.
 */
public class FiltroAcesso implements Filter {
    private static final Set<String> PUBLICOS = Set.of(
            "/", "/index.html", "/cadastro.html", "/api/login", "/api/cadastro", "/api/me", "/favicon.ico");

    @Override
    public void doFilter(ServletRequest rq, ServletResponse rs, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) rq;
        HttpServletResponse resp = (HttpServletResponse) rs;

        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("X-Frame-Options", "DENY");
        resp.setHeader("Referrer-Policy", "same-origin");
        resp.setHeader("Content-Security-Policy",
                "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'");

        String caminho = req.getServletPath();
        HttpSession sessao = req.getSession(false);
        boolean logado = sessao != null && sessao.getAttribute(ApiServlet.USUARIO) != null;

        if (logado || PUBLICOS.contains(caminho) || caminho.startsWith("/assets/")) {
            chain.doFilter(rq, rs);
        } else if (caminho.startsWith("/api/")) {
            resp.setStatus(401);
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write("{\"erro\":\"Faça login para continuar.\"}");
        } else {
            resp.sendRedirect("/index.html");
        }
    }
}
