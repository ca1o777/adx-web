package br.com.adx.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import br.com.adx.model.Sessao;

public interface SessaoRepository extends JpaRepository<Sessao, Long> {

    Optional<Sessao> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from Sessao s where s.tokenHash = :tokenHash")
    void apagarPorTokenHash(String tokenHash);

    @Modifying
    @Query("delete from Sessao s where s.expiraEm <= :agora")
    void apagarExpiradas(Instant agora);
}
