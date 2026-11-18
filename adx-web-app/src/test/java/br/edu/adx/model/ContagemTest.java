package br.edu.adx.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ContagemTest {
    @Test
    void listaVaziaTemTodosOsTotaisEAproveitamentoZerados() {
        Contagem contagem = Contagem.de(List.of());
        assertEquals(new Contagem(0, 0, 0, 0), contagem);
        assertEquals(0.0, contagem.aproveitamento());
    }

    @Test
    void calculaContagensDeResultadosMisturados() {
        Contagem contagem = Contagem.de(List.of(partida(1, "Vitória"), partida(2, "Vitória"), partida(3, "Empate"), partida(4, "Derrota")));
        assertEquals(new Contagem(4, 2, 1, 1), contagem);
        assertEquals(62.5, contagem.aproveitamento());
    }

    @ParameterizedTest
    @CsvSource({"1,1,0,0,100.0", "1,0,1,0,50.0", "1,0,0,1,0.0", "3,1,0,2,33.3", "3,2,0,1,66.7", "6,2,1,3,41.7", "26,10,5,11,48.1", "27,10,6,11,48.1"})
    void aproveitamentoPonderaEmpatesEArredondaUmaCasa(int total, int vitorias, int empates, int derrotas, double esperado) {
        assertEquals(esperado, new Contagem(total, vitorias, empates, derrotas).aproveitamento());
    }

    private static Partida partida(long id, String resultado) {
        return new Partida(id, id, "Adversário", "Blitz", resultado, LocalDateTime.of(2026, 1, 1, 12, 0));
    }
}
