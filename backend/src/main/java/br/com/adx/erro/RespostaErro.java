package br.com.adx.erro;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Formato de todas as respostas de erro da API: {"erro": "...", "campos": {"email": "..."}}.
 * "campos" só aparece quando o erro é de um campo do formulário, que o frontend destaca.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RespostaErro(String erro, Map<String, String> campos) {

    public RespostaErro(String erro) {
        this(erro, Map.of());
    }
}
