package br.edu.adx.util;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

/** Envia mensagens de conta pelo servidor SMTP configurado no ambiente. */
public class Correio {
    private static final Pattern URL_CAIXA_LOCAL = Pattern.compile("http://(?:127\\.0\\.0\\.1|\\[::1\\])(?::([0-9]{1,5}))?/?");
    private final String host;
    private final String remetente;
    private final String usuario;
    private final String senha;
    private final String modo;
    private final String caixaLocal;
    private final int porta;

    public Correio() {
        this(System.getenv());
    }

    Correio(Map<String, String> ambiente) {
        host = ambiente.getOrDefault("ADX_SMTP_HOST", "").strip();
        remetente = ambiente.getOrDefault("ADX_SMTP_REMETENTE", "").strip();
        usuario = ambiente.getOrDefault("ADX_SMTP_USUARIO", "").strip();
        senha = ambiente.getOrDefault("ADX_SMTP_SENHA", "");
        modo = ambiente.getOrDefault("ADX_SMTP_MODO", "starttls").strip();
        caixaLocal = ambiente.getOrDefault("ADX_SMTP_CAIXA_LOCAL_URL", "").strip();
        String padrao = "ssl".equals(modo) ? "465" : "587";
        porta = lerPorta(ambiente.getOrDefault("ADX_SMTP_PORTA", padrao));
    }

    public boolean configurado() {
        if (host.isEmpty() || porta < 1 || porta > 65_535 || remetente.isEmpty()) {
            return false;
        }
        if ("local".equals(modo)) {
            return ("127.0.0.1".equals(host) || "::1".equals(host)) && usuario.isEmpty() && senha.isEmpty();
        }
        return ("starttls".equals(modo) || "ssl".equals(modo)) && usuario.isEmpty() == senha.isEmpty();
    }

    public Optional<String> caixaDeEntrada() {
        var endereco = URL_CAIXA_LOCAL.matcher(caixaLocal);
        if (!"local".equals(modo) || !configurado() || !endereco.matches()) {
            return Optional.empty();
        }
        int portaHttp = endereco.group(1) == null ? 80 : Integer.parseInt(endereco.group(1));
        if (portaHttp < 1 || portaHttp > 65_535) {
            return Optional.empty();
        }
        return Optional.of(caixaLocal);
    }

    public void enviar(String destino, String assunto, String texto) throws IOException {
        if (!configurado()) {
            throw new IOException("O serviço SMTP não está configurado.");
        }
        Properties properties = new Properties();
        properties.setProperty("mail.smtp.host", host);
        properties.setProperty("mail.smtp.port", Integer.toString(porta));
        properties.setProperty("mail.smtp.auth", Boolean.toString(!usuario.isEmpty()));
        properties.setProperty("mail.smtp.connectiontimeout", "5000");
        properties.setProperty("mail.smtp.timeout", "5000");
        properties.setProperty("mail.smtp.writetimeout", "5000");
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        if ("starttls".equals(modo)) {
            properties.setProperty("mail.smtp.starttls.enable", "true");
            properties.setProperty("mail.smtp.starttls.required", "true");
        } else if ("ssl".equals(modo)) {
            properties.setProperty("mail.smtp.ssl.enable", "true");
        }

        Session session = Session.getInstance(properties);
        try {
            MimeMessage mensagem = new MimeMessage(session);
            mensagem.setFrom(endereco(remetente));
            mensagem.setRecipient(Message.RecipientType.TO, endereco(destino));
            mensagem.setSubject(assunto, StandardCharsets.UTF_8.name());
            mensagem.setText(texto, StandardCharsets.UTF_8.name());
            mensagem.setSentDate(new Date());
            if (usuario.isEmpty()) {
                Transport.send(mensagem);
            } else {
                Transport.send(mensagem, usuario, senha);
            }
        } catch (MessagingException e) {
            // Provedores podem colocar destinatários e respostas do SMTP na exceção.
            throw new IOException("O servidor SMTP não aceitou a mensagem.");
        }
    }

    private static InternetAddress endereco(String texto) throws MessagingException {
        InternetAddress endereco = new InternetAddress(texto, true);
        endereco.validate();
        if (endereco.isGroup() || endereco.getAddress().indexOf('@') <= 0) {
            throw new MessagingException("Endereço individual inválido.");
        }
        return endereco;
    }

    private static int lerPorta(String texto) {
        try {
            return Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
