package br.com.adx;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Relógio que só anda quando o teste manda, para conferir prazos (15 minutos, 30 dias) sem esperar. */
public class RelogioDeTeste extends Clock {

    private static final Instant INICIO = Instant.parse("2026-10-01T12:00:00Z");

    private Instant agora = INICIO;

    public void avancar(Duration duracao) {
        agora = agora.plus(duracao);
    }

    public void reiniciar() {
        agora = INICIO;
    }

    @Override
    public Instant instant() {
        return agora;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return this;
    }

    @TestConfiguration
    public static class Configuracao {

        @Bean
        @Primary
        public RelogioDeTeste relogioDeTeste() {
            return new RelogioDeTeste();
        }
    }
}
