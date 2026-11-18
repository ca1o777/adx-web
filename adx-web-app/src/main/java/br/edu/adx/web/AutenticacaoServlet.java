package br.edu.adx.web;

import br.edu.adx.dao.ErroBanco;
import br.edu.adx.dao.UsuarioDao;
import br.edu.adx.dao.UsuarioDao.Autenticacao;
import br.edu.adx.util.Formatos;
import br.edu.adx.util.Regras;
import br.edu.adx.util.Seguranca;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** UC01 (cadastro) e UC02 (login, logout e sessão). Regras RN001 e RN003. */
public class AutenticacaoServlet extends ApiServlet {
    private static final String CREDENCIAIS_INVALIDAS = "E-mail ou senha incorretos.";
    private final UsuarioDao usuarios = new UsuarioDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!"/api/me".equals(req.getServletPath())) {
            erro(resp, 405, "Método não permitido.");
            return;
        }
        HttpSession sessao = req.getSession(false);
        Long id = sessao == null ? null : (Long) sessao.getAttribute(USUARIO);
        if (id == null) {
            erro(resp, 401, "Não autenticado.");
            return;
        }
        usuarios.porId(id).ifPresentOrElse(u -> {
            try {
                json(resp, 200, Map.of("usuario", mapa(u)));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }, () -> {
            sessao.invalidate();
            try {
                erro(resp, 401, "Não autenticado.");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        switch (req.getServletPath()) {
            case "/api/cadastro" -> cadastrar(req, resp);
            case "/api/login" -> entrar(req, resp);
            case "/api/logout" -> sair(req, resp);
            default -> erro(resp, 404, "Recurso não encontrado.");
        }
    }

    /** RN001: cadastro com nome completo, e-mail único e válido e senha de no mínimo 8 caracteres. */
    private void cadastrar(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String nome = p(req, "nome").replaceAll("\\s+", " ");
        String email = p(req, "email").toLowerCase(Locale.ROOT);
        String senha = bruto(req, "senha");
        String confirmar = bruto(req, "confirmar");

        Map<String, String> erros = Regras.validarCadastro(nome, email, senha, confirmar);
        if (!erros.isEmpty()) {
            erros(resp, 400, erros);
            return;
        }
        if (usuarios.emailExiste(email)) {
            erros(resp, 409, Map.of("email", "Este e-mail já está cadastrado."));
            return;
        }
        try {
            usuarios.inserir(nome, email, Seguranca.gerarHash(senha), Regras.nomeExibicaoPadrao(nome));
        } catch (ErroBanco e) {
            if (e.violacaoUnica()) {
                erros(resp, 409, Map.of("email", "Este e-mail já está cadastrado."));
                return;
            }
            throw e;
        }
        json(resp, 201, Map.of("mensagem", "Conta criada com sucesso."));
    }

    /** RN003: sessão de até 30 dias e bloqueio de 15 minutos após 3 tentativas com senha incorreta. */
    private void entrar(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String email = p(req, "email").toLowerCase(Locale.ROOT);
        String senha = bruto(req, "senha");

        Map<String, String> erros = new LinkedHashMap<>();
        if (!Regras.emailValido(email)) {
            erros.put("email", "Informe um e-mail válido.");
        }
        if (senha.isEmpty()) {
            erros.put("senha", "Informe sua senha.");
        }
        if (!erros.isEmpty()) {
            erros(resp, 400, erros);
            return;
        }

        var achada = usuarios.autenticar(email, hash -> Seguranca.conferir(senha, hash));
        if (achada.isEmpty()) {
            Seguranca.conferir(senha, Seguranca.HASH_FALSO);   // gasta o mesmo tempo de um e-mail válido
            erro(resp, 401, CREDENCIAIS_INVALIDAS);
            return;
        }
        Autenticacao tentativa = achada.get();
        LocalDateTime agora = Formatos.agora();

        if (tentativa.bloqueadoAte() != null) {
            long minutos = Math.max(1, (long) Math.ceil(Duration.between(agora, tentativa.bloqueadoAte()).toSeconds() / 60.0));
            erro(resp, 423, "Acesso temporariamente bloqueado após " + Regras.TENTATIVAS_MAXIMAS
                    + " tentativas incorretas. Tente novamente em " + minutos + (minutos == 1 ? " minuto." : " minutos."));
            return;
        }
        if (!tentativa.autorizado()) {
            erro(resp, 401, CREDENCIAIS_INVALIDAS);
            return;
        }

        HttpSession antiga = req.getSession(false);
        if (antiga != null) {
            antiga.invalidate();                                 // evita fixação de sessão
        }
        HttpSession sessao = req.getSession(true);
        sessao.setAttribute(USUARIO, tentativa.usuarioId());
        sessao.setMaxInactiveInterval(Regras.SESSAO_DIAS * 24 * 60 * 60);
        json(resp, 200, Map.of("usuario", mapa(usuarios.porId(tentativa.usuarioId()).orElseThrow())));
    }

    private void sair(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession sessao = req.getSession(false);
        if (sessao != null) {
            sessao.invalidate();
        }
        json(resp, 200, Map.of("mensagem", "Sessão encerrada."));
    }
}
