package br.com.adx.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Corpo de POST /api/auth/cadastro. As regras são as mesmas que o frontend já confere. */
public record DadosCadastro(

        @NotBlank(message = NOME_INVALIDO)
        @Pattern(regexp = "\\S+( \\S+)+", message = NOME_INVALIDO)
        @Size(max = 100, message = "Use no máximo 100 caracteres.")
        String nome,

        @NotBlank(message = EMAIL_INVALIDO)
        @Pattern(regexp = "[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}", message = EMAIL_INVALIDO)
        @Size(max = 254, message = "Use no máximo 254 caracteres.")
        String email,

        @NotNull(message = SENHA_INVALIDA)
        @Size(min = 8, max = 72, message = SENHA_INVALIDA)
        String senha) {

    static final String NOME_INVALIDO = "Informe nome e sobrenome.";
    static final String EMAIL_INVALIDO = "Informe um e-mail válido.";
    public static final String SENHA_INVALIDA = "A senha precisa ter entre 8 e 72 caracteres.";

    public DadosCadastro {
        // "  Caio   Fernandes " vira "Caio Fernandes"; a senha fica como foi digitada
        nome = nome == null ? null : nome.strip().replaceAll("\\s+", " ");
        email = email == null ? null : email.strip();
    }
}
