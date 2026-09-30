package br.com.adx.seguranca;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Entrega o id do usuário logado aos parâmetros anotados com {@link UsuarioLogado}. */
public class UsuarioLogadoResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parametro) {
        return parametro.hasParameterAnnotation(UsuarioLogado.class)
                && Long.class.equals(parametro.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parametro, ModelAndViewContainer mavContainer,
            NativeWebRequest request, WebDataBinderFactory binderFactory) {
        Object usuarioId = request.getAttribute(UsuarioLogado.ATRIBUTO, RequestAttributes.SCOPE_REQUEST);
        if (usuarioId == null) {
            // rota usada sem passar pelo ExigeLoginInterceptor: nega em vez de seguir sem usuário
            throw ExigeLoginInterceptor.loginNecessario();
        }
        return usuarioId;
    }
}
