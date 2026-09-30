package br.com.adx.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.adx.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    boolean existsByEmail(String email);
}
