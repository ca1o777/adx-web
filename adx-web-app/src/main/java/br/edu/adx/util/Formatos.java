package br.edu.adx.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class Formatos {
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    public static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private Formatos() { }

    /** Data e hora atuais no horário de Brasília. */
    public static LocalDateTime agora() {
        return LocalDateTime.now(FUSO).withNano(0);
    }
}
