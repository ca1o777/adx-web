package br.edu.adx.web;

import br.edu.adx.dao.UsuarioDao;
import br.edu.adx.model.LinhaRanking;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ranking de jogadores: classificação pelo total de vitórias; desempate pelo aproveitamento. */
public class RankingServlet extends ApiServlet {
    private final UsuarioDao usuarios = new UsuarioDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long uid = usuarioId(req);
        List<Map<String, Object>> jogadores = new ArrayList<>();
        int posicao = 1;
        for (LinhaRanking l : usuarios.ranking()) {
            Map<String, Object> m = mapa(l.contagem());
            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("posicao", posicao++);
            linha.put("nome", l.nome());
            linha.putAll(m);
            linha.put("voce", l.usuarioId() == uid);
            jogadores.add(linha);
        }
        json(resp, 200, Map.of("jogadores", jogadores));
    }
}
