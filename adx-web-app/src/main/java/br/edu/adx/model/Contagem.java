package br.edu.adx.model;

import java.util.List;

/** Totais de vitórias, empates e derrotas (RN002). */
public record Contagem(int total, int vitorias, int empates, int derrotas) {

    public static Contagem de(List<Partida> partidas) {
        int v = 0, e = 0, d = 0;
        for (Partida p : partidas) {
            switch (p.resultado()) {
                case "Vitória" -> v++;
                case "Empate" -> e++;
                default -> d++;
            }
        }
        return new Contagem(partidas.size(), v, e, d);
    }

    /** Vitórias somadas à metade dos empates, divididas pelo total, em porcentagem com uma casa decimal. */
    public double aproveitamento() {
        return total == 0 ? 0 : Math.round(((vitorias + empates / 2.0) / total) * 1000) / 10.0;
    }
}
