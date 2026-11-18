package br.edu.adx.dao;

import java.sql.SQLException;

/** Erro de acesso ao banco de dados (envolve a SQLException para não poluir as assinaturas). */
public class ErroBanco extends RuntimeException {
    public ErroBanco(SQLException causa) {
        super(causa.getMessage(), causa);
    }

    /** Verdadeiro quando o erro é de chave duplicada (por exemplo, e-mail já cadastrado). */
    public boolean violacaoUnica() {
        return getCause() instanceof SQLException s && "23505".equals(s.getSQLState());
    }
}
