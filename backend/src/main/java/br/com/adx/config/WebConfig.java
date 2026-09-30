package br.com.adx.config;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Em desenvolvimento, o próprio Spring Boot serve as páginas do frontend, lidas direto da raiz
 * do repositório. Só os arquivos .html da raiz e a pasta assets/ ficam acessíveis; o restante
 * (backend/, adx.db, .git...) não é servido.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    private final String raizFrontend;

    public WebConfig(@Value("${adx.frontend.diretorio}") String diretorioFrontend) {
        Path raiz = Path.of(diretorioFrontend).toAbsolutePath().normalize();
        if (!Files.isRegularFile(raiz.resolve("index.html"))) {
            log.warn("Frontend não encontrado em {}. Rode a aplicação de dentro da pasta backend/ "
                    + "ou ajuste a propriedade adx.frontend.diretorio.", raiz);
        }
        String uri = raiz.toUri().toString();
        this.raizFrontend = uri.endsWith("/") ? uri : uri + "/";
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
}
