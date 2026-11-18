package br.edu.adx.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Partida registrada por um jogador. {@code numero} é a posição dela no histórico do jogador. */
public record Partida(long id, long numero, String adversario, String modo, String resultado,
                      LocalDateTime dataHora, LocalDate dataPartida) {
    public Partida(long id, long numero, String adversario, String modo, String resultado, LocalDateTime dataHora) {
        this(id, numero, adversario, modo, resultado, dataHora, dataHora.toLocalDate());
    }
}
