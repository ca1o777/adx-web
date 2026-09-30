package br.com.adx.seguranca;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca um parâmetro {@code Long} de um controller para receber o id do usuário logado.
 * O id é colocado na requisição pelo {@link ExigeLoginInterceptor}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface UsuarioLogado {

    /** Nome do atributo da requisição que guarda o id do usuário logado. */
    String ATRIBUTO = "adx.usuarioLogado";
}
