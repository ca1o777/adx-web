package br.edu.adx;

import br.edu.adx.dao.Banco;
import br.edu.adx.dao.FotoDao;
import br.edu.adx.web.AutenticacaoServlet;
import br.edu.adx.web.ContaServlet;
import br.edu.adx.web.FiltroAcesso;
import br.edu.adx.web.FotoServlet;
import br.edu.adx.web.PainelServlet;
import br.edu.adx.web.PartidasServlet;
import br.edu.adx.web.PerfilServlet;
import br.edu.adx.web.RankingServlet;
import jakarta.servlet.Servlet;
import jakarta.servlet.SessionCookieConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

/** Inicia o servidor Tomcat embutido: telas (HTML/CSS/JS), API em Servlets e banco H2. */
public class Main {

    public static void main(String[] args) throws Exception {
        int porta = Integer.parseInt(System.getProperty("adx.porta", System.getenv().getOrDefault("PORT", "8080")));
        Path web = Paths.get(System.getProperty("adx.webapp", "src/main/webapp")).toAbsolutePath();
        if (!Files.isDirectory(web)) {
            System.err.println("Pasta das telas nao encontrada: " + web
                    + "\nExecute a partir da raiz do projeto (a pasta adx-web-app).");
            System.exit(1);
        }

        Banco.iniciar();
        FotoDao.iniciar();
        if (Arrays.asList(args).contains("--demo")) {
            DadosDemo.popular();
        }

        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(Files.createTempDirectory("adx-tomcat").toString());
        tomcat.setPort(porta);
        String endereco = System.getProperty("adx.endereco");
        if (endereco != null) {
            tomcat.getConnector().setProperty("address", endereco);
        } else {
            tomcat.getConnector();
        }

        Context ctx = tomcat.addContext("", web.toString());
        ctx.setRequestCharacterEncoding("UTF-8");
        ctx.setResponseCharacterEncoding("UTF-8");
        ctx.setSessionTimeout(30 * 24 * 60);                       // RN003: sessão de até 30 dias (em minutos)
        ctx.addServletContainerInitializer((classes, sc) -> {
            SessionCookieConfig cookie = sc.getSessionCookieConfig();
            cookie.setName("ADXSESSAO");
            cookie.setHttpOnly(true);
            cookie.setMaxAge(30 * 24 * 60 * 60);
            cookie.setAttribute("SameSite", "Lax");
        }, null);

        // arquivos estáticos (telas)
        Wrapper padrao = Tomcat.addServlet(ctx, "default", new DefaultServlet());
        padrao.addInitParameter("listings", "false");
        ctx.addServletMappingDecoded("/", "default");
        ctx.addWelcomeFile("index.html");
        Map.of("html", "text/html", "css", "text/css", "js", "text/javascript", "woff2", "font/woff2",
                "png", "image/png", "svg", "image/svg+xml", "ico", "image/x-icon").forEach(ctx::addMimeMapping);

        // controle de acesso (RN003)
        FilterDef def = new FilterDef();
        def.setFilterName("acesso");
        def.setFilter(new FiltroAcesso());
        ctx.addFilterDef(def);
        FilterMap mapa = new FilterMap();
        mapa.setFilterName("acesso");
        mapa.addURLPattern("/*");
        ctx.addFilterMap(mapa);

        // API
        mapear(ctx, "autenticacao", new AutenticacaoServlet(), "/api/cadastro", "/api/login", "/api/logout", "/api/me");
        mapear(ctx, "partidas", new PartidasServlet(), "/api/partidas", "/api/partidas/*");
        mapear(ctx, "painel", new PainelServlet(), "/api/painel");
        mapear(ctx, "ranking", new RankingServlet(), "/api/ranking");
        mapear(ctx, "perfil", new PerfilServlet(), "/api/perfil");
        mapear(ctx, "foto-perfil", new FotoServlet(), "/api/perfil/foto");
        mapear(ctx, "conta", new ContaServlet(), "/api/conta/email/solicitar", "/api/conta/email/confirmar",
                "/api/conta/senha/solicitar", "/api/conta/senha/confirmar");

        tomcat.start();
        System.out.println();
        System.out.println("ADX no ar: http://localhost:" + porta);
        tomcat.getServer().await();
    }

    private static void mapear(Context ctx, String nome, Servlet servlet, String... padroes) {
        Tomcat.addServlet(ctx, nome, servlet);
        for (String padrao : padroes) {
            ctx.addServletMappingDecoded(padrao, nome);
        }
    }
}
