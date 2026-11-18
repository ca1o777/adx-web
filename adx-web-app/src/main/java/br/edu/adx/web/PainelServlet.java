package br.edu.adx.web;

import br.edu.adx.dao.PartidaDao;
import br.edu.adx.dao.UsuarioDao;
import br.edu.adx.model.Contagem;
import br.edu.adx.model.LinhaRanking;
import br.edu.adx.util.Formatos;
import br.edu.adx.util.Regras;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Painel do jogador: estatísticas acumuladas calculadas a partir do histórico (RN002). */
public class PainelServlet extends ApiServlet {
    private final PartidaDao partidas = new PartidaDao();
    private final UsuarioDao usuarios = new UsuarioDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long uid = usuarioId(req);

        List<Map<String, Object>> modos = new ArrayList<>();
        for (String modo : Regras.MODOS) {
            Map<String, Object> m = mapa(partidas.contagem(uid, modo));
            m.put("modo", modo);
            modos.add(m);
        }
        List<LinhaRanking> ranking = usuarios.ranking();
        int posicao = 0;
        for (int i = 0; i < ranking.size(); i++) {
            if (ranking.get(i).usuarioId() == uid) {
                posicao = i + 1;
            }
        }

        Map<String, Object> corpo = new LinkedHashMap<>();
        var evolucao = partidas.evolucao(uid);
        Contagem geral = evolucao.isEmpty() ? new Contagem(0, 0, 0, 0) : evolucao.get(evolucao.size() - 1).acumulada();
        corpo.put("resumo", mapa(geral));
        corpo.put("posicao", posicao);
        corpo.put("totalJogadores", ranking.size());
        corpo.put("ultimas", partidas.ultimas(uid, 5).stream().map(ApiServlet::mapa).toList());
        corpo.put("evolucao", evolucao.stream().map(ponto -> Map.of(
                "data", ponto.data().format(Formatos.DATA), "adversario", ponto.adversario(),
                "resultado", ponto.resultado(), "aproveitamento", ponto.acumulada().aproveitamento())).toList());
        corpo.put("modos", modos);
        json(resp, 200, corpo);
    }
}
