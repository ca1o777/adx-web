package br.com.adx.model;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "sessao")
public class Sessao {

    /** RN003: a sessão dura até 30 dias. */
    public static final Duration DURACAO = Duration.ofDays(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    /** Hash SHA-256 do token do cookie: quem lê o banco não consegue usar a sessão. */
    @Column(name = "token_hash", nullable = false, unique = true, updatable = false)
    private String tokenHash;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    @Column(name = "expira_em", nullable = false, updatable = false)
    private Instant expiraEm;

    protected Sessao() {
        // exigido pelo JPA
    }

    public Sessao(Usuario usuario, String tokenHash, Instant criadaEm) {
        this.usuario = usuario;
        this.tokenHash = tokenHash;
        this.criadaEm = criadaEm;
        this.expiraEm = criadaEm.plus(DURACAO);
    }

    public boolean expirou(Instant agora) {
        return !agora.isBefore(expiraEm);
    }

    public Usuario getUsuario() {
        return usuario;
    }
}
