package br.edu.adx.web;

import br.edu.adx.dao.UsuarioDao;
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
 * RN003: acesso público ao login, cadastro e recuperação de senha.
 * Também adiciona cabeçalhos de segurança a todas as respostas.
 */
public class FiltroAcesso implements Filter {
    private static final Set<String> PUBLICOS = Set.of(
            "/", "/index.html", "/cadastro.html", "/recuperar-senha.html", "/api/login", "/api/cadastro",
            "/api/me", "/api/conta/senha/solicitar", "/api/conta/senha/confirmar", "/favicon.ico");
    private final UsuarioDao usuarios = new UsuarioDao();

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
        if (caminho.startsWith("/assets/")) {
            chain.doFilter(rq, rs);
            return;
        }
        boolean logado = sessaoValida(req.getSession(false));

        if (logado || PUBLICOS.contains(caminho)) {
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

    private boolean sessaoValida(HttpSession sessao) {
        if (sessao == null) {
            return false;
        }
        try {
            Long id = (Long) sessao.getAttribute(ApiServlet.USUARIO);
            if (id == null) {
                return false;
            }
            Long versao = (Long) sessao.getAttribute(AutenticacaoServlet.VERSAO_CREDENCIAL);
            if (versao != null && usuarios.sessaoAtual(id, versao)) {
                return true;
            }
            sessao.invalidate();
        } catch (IllegalStateException encerrada) {
            // Outra requisição já encerrou a sessão.
        }
        return false;
    }
}
