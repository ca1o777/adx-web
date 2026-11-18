package br.edu.adx.web;

import br.edu.adx.dao.PartidaDao;
import br.edu.adx.dao.PartidaDao.Pagina;
import br.edu.adx.model.Contagem;
import br.edu.adx.model.Partida;
import br.edu.adx.util.Formatos;
import br.edu.adx.util.Regras;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UC03 (detalhes) e UC04 (histórico e registro de partidas).
 * RN002 e RN004: partidas registradas só podem ser consultadas, nunca alteradas ou excluídas.
 */
public class PartidasServlet extends ApiServlet {
    private static final String SOMENTE_CONSULTA =
            "Partidas registradas não podem ser alteradas nem excluídas, apenas consultadas.";
    private final PartidaDao partidas = new PartidaDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long uid = usuarioId(req);
        String info = req.getPathInfo();
        if (info == null || info.equals("/")) {
            listar(req, resp, uid);
        } else if (info.equals("/adversarios")) {
            json(resp, 200, Map.of("adversarios", partidas.adversarios(uid)));
        } else {
            detalhar(info.substring(1), resp, uid);
        }
    }

    /** Histórico com filtros por adversário, modo e resultado, em páginas. */
    private void listar(HttpServletRequest req, HttpServletResponse resp, long uid) throws IOException {
        String busca = p(req, "busca");
        String modo = p(req, "modo");
        String resultado = p(req, "resultado");
        if ((!modo.isEmpty() && !Regras.MODOS.contains(modo))
                || (!resultado.isEmpty() && !Regras.RESULTADOS.contains(resultado))) {
            erro(resp, 400, "Filtro inválido.");
            return;
        }
        int porPagina = Math.min(50, Math.max(1, inteiro(req, "porPagina", 10)));
        int pagina = Math.max(1, inteiro(req, "pagina", 1));

        Pagina pg = partidas.listar(uid, busca, modo, resultado, pagina, porPagina);
        int paginas = Math.max(1, (int) ((pg.total() + (long) porPagina - 1) / porPagina));
        if (pagina > paginas) {
            pagina = paginas;
            pg = partidas.listar(uid, busca, modo, resultado, pagina, porPagina);
        }
        int de = pg.total() == 0 ? 0 : (pagina - 1) * porPagina + 1;

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("total", pg.total());
        corpo.put("pagina", pagina);
        corpo.put("paginas", paginas);
        corpo.put("porPagina", porPagina);
        corpo.put("de", de);
        corpo.put("ate", pg.total() == 0 ? 0 : de - 1 + pg.itens().size());
        corpo.put("itens", pg.itens().stream().map(ApiServlet::mapa).toList());
        json(resp, 200, corpo);
    }

    /** Detalhes de uma partida e retrospecto contra o mesmo adversário. */
    private void detalhar(String texto, HttpServletResponse resp, long uid) throws IOException {
        long id;
        try {
            id = Long.parseLong(texto);
        } catch (NumberFormatException e) {
            erro(resp, 404, "Partida não encontrada.");
            return;
        }
        var achada = partidas.buscar(uid, id);
        if (achada.isEmpty()) {
            erro(resp, 404, "Partida não encontrada.");
            return;
        }
        Partida partida = achada.get();
        List<Partida> contra = partidas.contra(uid, partida.adversario());

        Map<String, Object> retrospecto = new LinkedHashMap<>();
        retrospecto.put("adversario", partida.adversario());
        retrospecto.put("resumo", mapa(Contagem.de(contra)));
        retrospecto.put("partidas", contra.stream().map(ApiServlet::mapa).toList());

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("partida", mapa(partida));
        corpo.put("totalPartidas", partidas.contagem(uid, "").total());
        corpo.put("retrospecto", retrospecto);
        json(resp, 200, corpo);
    }

    /** RN004: registro com adversário, modo e resultado; a data é gerada pelo servidor. */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (req.getPathInfo() != null && !req.getPathInfo().equals("/")) {
            erro(resp, 404, "Recurso não encontrado.");
            return;
        }
        long uid = usuarioId(req);
        String adversario = p(req, "adversario").replaceAll("\\s+", " ");
        String modo = p(req, "modo");
        String resultado = p(req, "resultado");

        Map<String, String> erros = Regras.validarPartida(adversario, modo, resultado);
        if (!erros.isEmpty()) {
            erros(resp, 400, erros);
            return;
        }
        long id = partidas.inserir(uid, adversario, modo, resultado, Formatos.agora());
        json(resp, 201, Map.of("partida", mapa(partidas.buscar(uid, id).orElseThrow())));
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        somenteConsulta(resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        somenteConsulta(resp);
    }

    private void somenteConsulta(HttpServletResponse resp) throws IOException {
        resp.setHeader("Allow", "GET, POST");
        erro(resp, 405, SOMENTE_CONSULTA);
    }
}
