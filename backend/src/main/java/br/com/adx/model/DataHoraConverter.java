package br.com.adx.model;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Grava datas no SQLite como texto ISO-8601 em UTC, sempre com o mesmo tamanho
 * (ex.: 2026-09-27T21:14:05Z). Assim a ordem alfabética do texto é a ordem cronológica,
 * e comparações e ORDER BY funcionam direto no banco.
 */
@Converter(autoApply = true)
public class DataHoraConverter implements AttributeConverter<Instant, String> {

    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    @Override
    public String convertToDatabaseColumn(Instant instante) {
        return instante == null ? null : FORMATO.format(instante.truncatedTo(ChronoUnit.SECONDS));
    }

    @Override
    public Instant convertToEntityAttribute(String texto) {
        return texto == null ? null : Instant.parse(texto);
    }
}
