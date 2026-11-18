package br.edu.adx.web;

import br.edu.adx.dao.FotoDao;
import br.edu.adx.util.FotoPerfil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/** Consulta, atualiza e remove a foto da conta autenticada. */
public final class FotoServlet extends ApiServlet {
    private final FotoDao fotos = new FotoDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        var foto = fotos.porUsuario(usuarioId(req));
        if (foto.isEmpty()) {
            erro(resp, 404, "Nenhuma foto de perfil cadastrada.");
            return;
        }
        byte[] png = foto.get();
        resp.setContentType("image/png");
        resp.setContentLength(png.length);
        resp.setHeader("Cache-Control", "no-store");
        resp.getOutputStream().write(png);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String tipo = req.getContentType();
        if (!"image/jpeg".equalsIgnoreCase(tipo) && !"image/png".equalsIgnoreCase(tipo)) {
            erro(resp, 400, "Selecione uma imagem JPG ou PNG.");
            return;
        }
        if (req.getContentLengthLong() > FotoPerfil.LIMITE_BYTES) {
            erro(resp, 413, "O arquivo da foto deve ter no máximo 2 MB.");
            return;
        }
        byte[] entrada = req.getInputStream().readNBytes(FotoPerfil.LIMITE_BYTES + 1);
        if (entrada.length > FotoPerfil.LIMITE_BYTES) {
            erro(resp, 413, "O arquivo da foto deve ter no máximo 2 MB.");
            return;
        }
        byte[] png;
        try {
            png = FotoPerfil.normalizar(entrada, tipo);
        } catch (IOException | IllegalArgumentException | IndexOutOfBoundsException e) {
            erro(resp, 400, "Use uma imagem JPG ou PNG válida, com até 2048 pixels por lado.");
            return;
        }
        fotos.salvar(usuarioId(req), png);
        json(resp, 200, Map.of("mensagem", "Foto de perfil atualizada."));
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        fotos.remover(usuarioId(req));
        json(resp, 200, Map.of("mensagem", "Foto de perfil removida."));
    }
}
