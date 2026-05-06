package com.precificacao.precificacao.repository;

import com.precificacao.precificacao.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailAndAtivoTrue(String email);
}