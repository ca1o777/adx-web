package br.edu.adx.web;

import br.edu.adx.dao.ErroBanco;
import br.edu.adx.model.Contagem;
import br.edu.adx.model.Partida;
import br.edu.adx.model.Usuario;
import br.edu.adx.util.Formatos;
import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Base dos Servlets da API: respostas em JSON, tratamento de erros e proteção contra requisições forjadas (CSRF). */
public abstract class ApiServlet extends HttpServlet {
    protected static final Gson GSON = new Gson();
    /** Nome do atributo da sessão que guarda o id do jogador autenticado. */
    public static final String USUARIO = "usuarioId";

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String metodo = req.getMethod();
        boolean altera = !(metodo.equals("GET") || metodo.equals("HEAD") || metodo.equals("OPTIONS"));
        if (altera && !"ADX".equals(req.getHeader("X-Requested-With"))) {
            erro(resp, 403, "Requisição não autorizada.");
            return;
        }
        try {
            super.service(req, resp);
        } catch (ErroBanco e) {
            log("Erro de banco de dados", e);
            if (!resp.isCommitted()) {
                erro(resp, 500, "Erro interno ao acessar o banco de dados.");
            }
        }
    }

    // ----- respostas -----

    protected void json(HttpServletResponse resp, int status, Object corpo) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write(GSON.toJson(corpo));
    }

    protected void erro(HttpServletResponse resp, int status, String mensagem) throws IOException {
        json(resp, status, Map.of("erro", mensagem));
    }

    protected void erros(HttpServletResponse resp, int status, Map<String, String> campos) throws IOException {
        json(resp, status, Map.of("erros", campos));
    }

    // ----- entrada -----

    /** Parâmetro de texto sem espaços nas pontas (nunca nulo). */
    protected static String p(HttpServletRequest req, String nome) {
        String v = req.getParameter(nome);
        return v == null ? "" : v.trim();
    }

    /** Parâmetro sem alterações (usado nas senhas). */
    protected static String bruto(HttpServletRequest req, String nome) {
        String v = req.getParameter(nome);
        return v == null ? "" : v;
    }

    protected static int inteiro(HttpServletRequest req, String nome, int padrao) {
        try {
            return Integer.parseInt(p(req, nome));
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    protected static long usuarioId(HttpServletRequest req) {
        HttpSession sessao = req.getSession(false);
        Long id = sessao == null ? null : (Long) sessao.getAttribute(USUARIO);
        if (id == null) {
            throw new IllegalStateException("Requisição sem usuário autenticado");
        }
        return id;
    }

    // ----- conversão para JSON -----

    protected static Map<String, Object> mapa(Usuario u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.id());
        m.put("nome", u.nome());
        m.put("email", u.email());
        m.put("nomeExibicao", u.nomeExibicao());
        m.put("modoPreferido", u.modoPreferido());
        m.put("sobre", u.sobre());
        m.put("iniciais", u.iniciais());
        return m;
    }

    protected static Map<String, Object> mapa(Partida p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id());
        m.put("numero", p.numero());
        m.put("adversario", p.adversario());
        m.put("modo", p.modo());
        m.put("resultado", p.resultado());
        m.put("data", p.dataPartida().format(Formatos.DATA));
        m.put("hora", p.dataHora().format(Formatos.HORA));
        m.put("registradaEm", p.dataHora().format(Formatos.DATA) + " às " + p.dataHora().format(Formatos.HORA));
        return m;
    }

    protected static Map<String, Object> mapa(Contagem c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", c.total());
        m.put("vitorias", c.vitorias());
        m.put("empates", c.empates());
        m.put("derrotas", c.derrotas());
        m.put("aproveitamento", c.aproveitamento());
        return m;
    }
}
