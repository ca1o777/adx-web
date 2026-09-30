package br.com.adx.erro;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Converte os erros da aplicação e do Spring MVC para o formato {@link RespostaErro}. */
@RestControllerAdvice
public class TratadorDeErros extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<RespostaErro> regraDeNegocio(RegraNegocioException e) {
        return ResponseEntity.status(e.getStatus()).body(new RespostaErro(e.getMessage(), e.getCampos()));
    }

    /** Falhas de validação (@Valid): uma mensagem por campo, que o frontend mostra embaixo de cada um. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(erro -> campos.putIfAbsent(erro.getField(), erro.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new RespostaErro("Confira os campos destacados.", campos));
    }

    /** Demais erros do Spring MVC (rota inexistente, método não permitido, JSON malformado...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(status).headers(headers).body(new RespostaErro(mensagemPara(status)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespostaErro> erroInesperado(Exception e) {
        log.error("Erro inesperado", e);
        return ResponseEntity.internalServerError()
                .body(new RespostaErro("Erro inesperado no servidor. Tente novamente."));
    }

    private static String mensagemPara(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Requisição inválida.";
            case 404 -> "Recurso não encontrado.";
            case 405 -> "Operação não permitida.";
            case 415 -> "Formato de dados não suportado.";
            default -> "Não foi possível concluir a operação.";
        };
    }
}
