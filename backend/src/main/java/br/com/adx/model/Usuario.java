package br.com.adx.model;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuario")
public class Usuario {

    /** RN003: 3 senhas erradas seguidas bloqueiam o acesso por 15 minutos. */
    public static final int SENHAS_ERRADAS_PARA_BLOQUEIO = 3;
    public static final Duration DURACAO_DO_BLOQUEIO = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_completo", nullable = false)
    private String nomeCompleto;

    @Column(name = "nome_exibicao", nullable = false)
    private String nomeExibicao;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(name = "tentativas_falhas", nullable = false)
    private int tentativasFalhas;

    @Column(name = "bloqueado_ate")
    private Instant bloqueadoAte;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected Usuario() {
        // exigido pelo JPA
    }

    public Usuario(String nomeCompleto, String nomeExibicao, String email, String senhaHash, Instant criadoEm) {
        this.nomeCompleto = nomeCompleto;
        this.nomeExibicao = nomeExibicao;
        this.email = email;
        this.senhaHash = senhaHash;
        this.criadoEm = criadoEm;
    }

    public boolean estaBloqueado(Instant agora) {
        return bloqueadoAte != null && agora.isBefore(bloqueadoAte);
    }

    /** Conta uma senha errada; na terceira seguida, bloqueia o acesso por 15 minutos e zera a contagem. */
    public void registrarSenhaErrada(Instant agora) {
        tentativasFalhas++;
        if (tentativasFalhas >= SENHAS_ERRADAS_PARA_BLOQUEIO) {
            tentativasFalhas = 0;
            bloqueadoAte = agora.plus(DURACAO_DO_BLOQUEIO);
        }
    }

    /** Login certo: a sequência de senhas erradas recomeça do zero. */
    public void registrarLoginCorreto() {
        tentativasFalhas = 0;
        bloqueadoAte = null;
    }

    public Long getId() {
        return id;
    }

    public String getNomeCompleto() {
        return nomeCompleto;
    }

    public String getNomeExibicao() {
        return nomeExibicao;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Instant getBloqueadoAte() {
        return bloqueadoAte;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
