package br.com.adx.config;

import java.time.Clock;
import java.time.ZoneOffset;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RelogioConfig {

    /**
     * Relógio da aplicação, em UTC e com precisão de segundos (a mesma gravada no banco).
     * Os testes trocam por um relógio controlável para conferir prazos sem esperar.
     */
    @Bean
    public Clock relogio() {
        return Clock.tickSeconds(ZoneOffset.UTC);
    }
}
