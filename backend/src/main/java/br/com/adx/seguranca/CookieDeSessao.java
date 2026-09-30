package br.com.adx.seguranca;

import java.util.Optional;

import org.springframework.http.ResponseCookie;
import org.springframework.web.util.WebUtils;

import br.com.adx.model.Sessao;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Cookie que guarda o token da sessão. HttpOnly: o JavaScript da página não consegue lê-lo.
 * SameSite=Lax: o navegador não o envia em requisições POST vindas de outros sites.
 */
public final class CookieDeSessao {

    public static final String NOME = "ADX_SESSAO";

    private CookieDeSessao() {
    }

    public static ResponseCookie criar(String token, boolean conexaoSegura) {
        return base(token, conexaoSegura).maxAge(Sessao.DURACAO).build();
    }

    public static ResponseCookie apagar(boolean conexaoSegura) {
        return base("", conexaoSegura).maxAge(0).build();
    }

    public static Optional<String> ler(HttpServletRequest request) {
        return Optional.ofNullable(WebUtils.getCookie(request, NOME))
                .map(Cookie::getValue)
                .filter(valor -> !valor.isBlank());
    }

    private static ResponseCookie.ResponseCookieBuilder base(String valor, boolean conexaoSegura) {
        return ResponseCookie.from(NOME, valor)
                .httpOnly(true)
                .secure(conexaoSegura)
                .sameSite("Lax")
                .path("/");
    }
}
