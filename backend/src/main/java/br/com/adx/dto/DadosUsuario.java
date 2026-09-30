package br.com.adx.dto;

import br.com.adx.model.Usuario;

/** Dados do usuário devolvidos pela API. Nunca inclui a senha nem o hash dela. */
public record DadosUsuario(String nomeCompleto, String nomeExibicao, String email) {

    public static DadosUsuario de(Usuario usuario) {
        return new DadosUsuario(usuario.getNomeCompleto(), usuario.getNomeExibicao(), usuario.getEmail());
    }
}
