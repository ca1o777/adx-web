package br.com.adx.seguranca;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.adx.model.Sessao;
import br.com.adx.model.Usuario;
import br.com.adx.repository.SessaoRepository;

/** Sessões de login (RN003), guardadas no banco para durar até 30 dias mesmo se o servidor reiniciar. */
@Service
public class SessaoService {

    private static final int BYTES_DO_TOKEN = 32;

    private final SessaoRepository sessoes;
    private final Clock relogio;
    private final SecureRandom geradorDeTokens = new SecureRandom();

    public SessaoService(SessaoRepository sessoes, Clock relogio) {
        this.sessoes = sessoes;
        this.relogio = relogio;
    }

    /** Abre uma sessão e devolve o token que vai no cookie. No banco fica só o hash do token. */
    @Transactional
    public String abrir(Usuario usuario) {
        Instant agora = relogio.instant();
        sessoes.apagarExpiradas(agora);
        byte[] bytes = new byte[BYTES_DO_TOKEN];
        geradorDeTokens.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessoes.save(new Sessao(usuario, hash(token), agora));
        return token;
    }

    /** Id do usuário dono do token, se a sessão existir e ainda não tiver expirado. */
    @Transactional
    public Optional<Long> usuarioDoToken(String token) {
        return sessoes.findByTokenHash(hash(token)).flatMap(sessao -> {
            if (sessao.expirou(relogio.instant())) {
                sessoes.delete(sessao);
                return Optional.empty();
            }
            return Optional.of(sessao.getUsuario().getId());
        });
    }

    @Transactional
    public void encerrar(String token) {
        sessoes.apagarPorTokenHash(hash(token));
    }

    static String hash(String token) {
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
