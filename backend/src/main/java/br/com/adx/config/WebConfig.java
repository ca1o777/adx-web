package br.com.adx.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import br.com.adx.seguranca.ExigeLoginInterceptor;
import br.com.adx.seguranca.ExigeLoginInterceptor.SemLogin;
import br.com.adx.seguranca.JaLogadoInterceptor;
import br.com.adx.seguranca.SessaoService;
import br.com.adx.seguranca.UsuarioLogadoResolver;

/**
 * Em desenvolvimento, o próprio Spring Boot serve as páginas do frontend, lidas direto da raiz
 * do repositório. Só os arquivos .html da raiz e a pasta assets/ ficam acessíveis; o restante
 * (backend/, adx.db, .git...) não é servido.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    /** RN003: as únicas telas acessíveis sem login. */
    private static final String[] PAGINAS_PUBLICAS = { "/index.html", "/cadastro.html" };
    private static final String[] API_PUBLICA = { "/api/auth/cadastro", "/api/auth/login", "/api/auth/logout" };

    private final String raizFrontend;
    private final SessaoService sessoes;

    public WebConfig(@Value("${adx.frontend.diretorio}") String diretorioFrontend, SessaoService sessoes) {
        Path raiz = Path.of(diretorioFrontend).toAbsolutePath().normalize();
        if (!Files.isRegularFile(raiz.resolve("index.html"))) {
            log.warn("Frontend não encontrado em {}. Rode a aplicação de dentro da pasta backend/ "
                    + "ou ajuste a propriedade adx.frontend.diretorio.", raiz);
        }
        String uri = raiz.toUri().toString();
        this.raizFrontend = uri.endsWith("/") ? uri : uri + "/";
        this.sessoes = sessoes;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // no-cache: o navegador sempre confere se o arquivo mudou, então edições aparecem ao recarregar
        registry.addResourceHandler("/*.html")
                .addResourceLocations(raizFrontend)
                .setCacheControl(CacheControl.noCache());
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(raizFrontend + "assets/")
                .setCacheControl(CacheControl.noCache());
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/index.html");
    }

    /** RN003: sem login, a API responde 401 e as páginas internas levam à tela de login. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ExigeLoginInterceptor(sessoes, SemLogin.RESPONDER_401))
                .addPathPatterns("/api/**")
                .excludePathPatterns(API_PUBLICA);
        registry.addInterceptor(new ExigeLoginInterceptor(sessoes, SemLogin.IR_PARA_O_LOGIN))
                .addPathPatterns("/*.html")
                .excludePathPatterns(PAGINAS_PUBLICAS);
        registry.addInterceptor(new JaLogadoInterceptor(sessoes))
                .addPathPatterns(PAGINAS_PUBLICAS);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new UsuarioLogadoResolver());
    }
}
