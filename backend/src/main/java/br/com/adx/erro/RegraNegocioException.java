package br.com.adx.erro;

import java.util.Map;

import org.springframework.http.HttpStatus;

/** Erro esperado de uma regra de negócio: a mensagem é a que o usuário vai ver. */
public class RegraNegocioException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, String> campos;

    public RegraNegocioException(HttpStatus status, String mensagem) {
        this(status, mensagem, Map.of());
    }

    private RegraNegocioException(HttpStatus status, String mensagem, Map<String, String> campos) {
        super(mensagem);
        this.status = status;
        this.campos = campos;
    }

    /** Erro ligado a um campo do formulário. */
    public static RegraNegocioException doCampo(HttpStatus status, String campo, String mensagem) {
        return new RegraNegocioException(status, mensagem, Map.of(campo, mensagem));
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, String> getCampos() {
        return campos;
    }
}
