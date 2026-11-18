package br.edu.adx.model;

/** Jogador cadastrado na aplicação (a senha nunca é carregada neste objeto). */
public record Usuario(long id, String nome, String email, String nomeExibicao, String modoPreferido, String sobre) {

    /** Iniciais usadas no avatar (até duas letras). */
    public String iniciais() {
        String[] p = nomeExibicao.trim().split("\\s+");
        String r = p[0].substring(0, 1) + (p.length > 1 ? p[1].substring(0, 1) : "");
        return r.toUpperCase();
    }
}
