package br.edu.adx.web;

import br.edu.adx.dao.UsuarioDao;
import br.edu.adx.util.Regras;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/** UC05: consulta e atualização do perfil do jogador (RN005). */
public class PerfilServlet extends ApiServlet {
    private final UsuarioDao usuarios = new UsuarioDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        var usuario = usuarios.porId(usuarioId(req));
        if (usuario.isEmpty()) {
            erro(resp, 404, "Usuário não encontrado.");
            return;
        }
        json(resp, 200, Map.of("usuario", mapa(usuario.get())));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long uid = usuarioId(req);
        String nomeExibicao = p(req, "nomeExibicao").replaceAll("\\s+", " ");
        String modo = p(req, "modoPreferido");
        String sobre = p(req, "sobre");

        Map<String, String> erros = Regras.validarPerfil(nomeExibicao, modo, sobre);
        if (!erros.isEmpty()) {
            erros(resp, 400, erros);
            return;
        }
        usuarios.atualizarPerfil(uid, nomeExibicao, modo, sobre);
        json(resp, 200, Map.of("usuario", mapa(usuarios.porId(uid).orElseThrow())));
    }
}
