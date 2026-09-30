package br.com.adx.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/** Corpo de POST /api/auth/login. */
public record DadosLogin(

        @NotBlank(message = "Informe um e-mail válido.")
        String email,

        @NotEmpty(message = "Informe sua senha.")
        String senha) {

    public DadosLogin {
        email = email == null ? null : email.strip();
    }
}
