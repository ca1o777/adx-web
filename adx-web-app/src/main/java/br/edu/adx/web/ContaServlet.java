package br.edu.adx.web;

import br.edu.adx.dao.ContaDao;
import br.edu.adx.dao.ContaDao.Envio;
import br.edu.adx.dao.ContaDao.Estado;
import br.edu.adx.dao.ErroBanco;
import br.edu.adx.dao.UsuarioDao;
import br.edu.adx.util.Correio;
import br.edu.adx.util.Regras;
import br.edu.adx.util.Seguranca;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** RN005: confirmação de alteração do e-mail e recuperação da senha. */
public class ContaServlet extends ApiServlet {
    private static final Pattern CODIGO = Pattern.compile("[2-9A-HJ-NP-Z]{16}");
    private static final String CODIGO_INVALIDO = "Código inválido ou expirado. Confira o código ou solicite um novo.";
    private static final String CORREIO_INDISPONIVEL = "Serviço de e-mail indisponível. Tente novamente mais tarde.";
    private static final String SESSAO_EXPIRADA = "Faça login para continuar.";
    private static final String EMAIL_EM_USO = "Este e-mail já está cadastrado.";
    private static final String SENHA_SOLICITADA = "Se o e-mail estiver cadastrado, você receberá um código de recuperação.";

    private final ContaDao contas = new ContaDao();
    private final UsuarioDao usuarios = new UsuarioDao();
    private final Correio correio = new Correio();
    private final ThreadPoolExecutor recuperacoes = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), tarefa -> {
                Thread thread = new Thread(tarefa, "adx-recuperacao");
                thread.setDaemon(true);
                return thread;
            });

    @Override
    public void destroy() {
        recuperacoes.shutdownNow();
        super.destroy();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        switch (req.getServletPath()) {
            case "/api/conta/email/solicitar" -> solicitarEmail(req, resp);
            case "/api/conta/email/confirmar" -> confirmarEmail(req, resp);
            case "/api/conta/senha/solicitar" -> solicitarSenha(req, resp);
            case "/api/conta/senha/confirmar" -> confirmarSenha(req, resp);
            default -> erro(resp, 404, "Recurso não encontrado.");
        }
    }

    private void solicitarEmail(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Sessao sessao = sessao(req);
        if (sessao == null) {
            erro(resp, 401, SESSAO_EXPIRADA);
            return;
        }
        String novoEmail = email(req);
        String senha = bruto(req, "senha");
        Map<String, String> campos = new LinkedHashMap<>();
        if (!Regras.emailValido(novoEmail)) {
            campos.put("email", "Informe um e-mail válido.");
        }
        if (senha.isEmpty() || senha.length() > 72) {
            campos.put("senha", "Informe sua senha atual.");
        }
        if (!campos.isEmpty()) {
            erros(resp, 400, campos);
            return;
        }
        if (!correio.configurado()) {
            erro(resp, 503, CORREIO_INDISPONIVEL);
            return;
        }
        var tentativa = usuarios.reautenticar(sessao.id(), sessao.versao(), hash -> Seguranca.conferir(senha, hash));
        if (tentativa.isEmpty()) {
            erro(resp, 401, SESSAO_EXPIRADA);
            return;
        }
        if (tentativa.get().bloqueadoAte() != null) {
            erro(resp, 423, "Senha temporariamente bloqueada. Tente novamente mais tarde.");
            return;
        }
        if (!tentativa.get().autorizado()) {
            erro(resp, 403, "Senha atual incorreta.");
            return;
        }
        var solicitacao = contas.solicitarEmail(sessao.id(), sessao.versao(), novoEmail);
        switch (solicitacao.estado()) {
            case SESSAO_EXPIRADA -> erro(resp, 401, SESSAO_EXPIRADA);
            case EMAIL_IGUAL -> erros(resp, 400, Map.of("email", "Informe um e-mail diferente do atual."));
            case EMAIL_EM_USO -> erros(resp, 409, Map.of("email", EMAIL_EM_USO));
            case AGUARDE -> erro(resp, 429, "Aguarde um minuto entre solicitações de código.");
            case PRONTO -> {
                Envio envio = solicitacao.envio();
                try {
                    correio.enviar(envio.destino(), "ADX - Confirmação de alteração de e-mail",
                            corpo("Foi solicitada a alteração do e-mail de acesso para " + novoEmail + ".", envio.codigo()));
                } catch (IOException e) {
                    log("Falha no envio de código de alteração de e-mail.");
                    erro(resp, 503, CORREIO_INDISPONIVEL);
                    return;
                }
                responderSolicitacao(resp, "Código enviado ao e-mail atual da conta.");
            }
            default -> throw new IllegalStateException("Estado inesperado na solicitação de e-mail.");
        }
    }

    private void confirmarEmail(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Sessao sessao = sessao(req);
        if (sessao == null) {
            erro(resp, 401, SESSAO_EXPIRADA);
            return;
        }
        String codigo = codigo(req);
        if (!CODIGO.matcher(codigo).matches()) {
            erros(resp, 400, Map.of("codigo", "Informe os 16 caracteres do código enviado."));
            return;
        }
        Estado resultado = contas.confirmarEmail(sessao.id(), sessao.versao(), codigo);
        switch (resultado) {
            case CONFIRMADO -> json(resp, 200, Map.of("mensagem", "E-mail atualizado com sucesso.",
                    "usuario", mapa(usuarios.porId(sessao.id()).orElseThrow())));
            case SESSAO_EXPIRADA -> erro(resp, 401, SESSAO_EXPIRADA);
            case EMAIL_EM_USO -> erro(resp, 409, EMAIL_EM_USO + " Solicite uma nova alteração.");
            case INVALIDO -> erro(resp, 400, CODIGO_INVALIDO);
            default -> throw new IllegalStateException("Estado inesperado na confirmação de e-mail.");
        }
    }

    private void solicitarSenha(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String email = email(req);
        if (!Regras.emailValido(email)) {
            erros(resp, 400, Map.of("email", "Informe um e-mail válido."));
            return;
        }
        if (!correio.configurado()) {
            erro(resp, 503, CORREIO_INDISPONIVEL);
            return;
        }
        try {
            // O acesso ao banco e a entrega ocorrem fora da requisição, com a mesma resposta pública para qualquer endereço.
            recuperacoes.execute(() -> entregarRecuperacao(email));
        } catch (RejectedExecutionException e) {
            erro(resp, 503, "Muitas solicitações de recuperação. Tente novamente mais tarde.");
            return;
        }
        responderSolicitacao(resp, SENHA_SOLICITADA);
    }

    private void responderSolicitacao(HttpServletResponse resp, String mensagem) throws IOException {
        Map<String, String> resposta = new LinkedHashMap<>();
        resposta.put("mensagem", mensagem);
        correio.caixaDeEntrada().ifPresent(url -> resposta.put("caixaDeEntrada", url));
        json(resp, 202, resposta);
    }

    private void entregarRecuperacao(String email) {
        try {
            var encontrado = contas.solicitarSenha(email);
            if (encontrado.isPresent()) {
                Envio envio = encontrado.get();
                correio.enviar(envio.destino(), "ADX - Recuperação de senha",
                        corpo("Foi solicitada a recuperação da senha da sua conta ADX.", envio.codigo()));
            }
        } catch (IOException | ErroBanco e) {
            log("Falha no processamento de uma solicitação de recuperação de senha.");
        }
    }

    private void confirmarSenha(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String email = email(req);
        String codigo = codigo(req);
        String senha = bruto(req, "senha");
        String confirmar = bruto(req, "confirmar");
        Map<String, String> campos = new LinkedHashMap<>();
        if (!Regras.emailValido(email)) {
            campos.put("email", "Informe um e-mail válido.");
        }
        if (!CODIGO.matcher(codigo).matches()) {
            campos.put("codigo", "Informe os 16 caracteres do código enviado.");
        }
        if (senha.length() < Regras.SENHA_MINIMA || senha.length() > 72) {
            campos.put("senha", "Use de 8 a 72 caracteres.");
        }
        if (confirmar.isEmpty() || !senha.equals(confirmar)) {
            campos.put("confirmar", "As senhas não são iguais.");
        }
        if (!campos.isEmpty()) {
            erros(resp, 400, campos);
            return;
        }
        if (contas.confirmarSenha(email, codigo, senha) == Estado.CONFIRMADO) {
            json(resp, 200, Map.of("mensagem", "Senha atualizada. Entre com a nova senha."));
        } else {
            erro(resp, 400, CODIGO_INVALIDO);
        }
    }

    private static String corpo(String introducao, String codigo) {
        return introducao + "\n\nSeu código é: " + codigo + "\n\nO código é válido por "
                + ContaDao.VALIDADE_MINUTOS + " minutos. Se não fez esta solicitação, ignore esta mensagem.";
    }

    private static String email(HttpServletRequest req) {
        return p(req, "email").toLowerCase(Locale.ROOT);
    }

    private static String codigo(HttpServletRequest req) {
        return p(req, "codigo").toUpperCase(Locale.ROOT);
    }

    private record Sessao(long id, long versao) { }

    private static Sessao sessao(HttpServletRequest req) {
        HttpSession sessao = req.getSession(false);
        if (sessao != null && sessao.getAttribute(USUARIO) instanceof Long id
                && sessao.getAttribute(AutenticacaoServlet.VERSAO_CREDENCIAL) instanceof Long versao) {
            return new Sessao(id, versao);
        }
        return null;
    }
}
