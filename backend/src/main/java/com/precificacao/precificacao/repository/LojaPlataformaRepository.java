package com.precificacao.precificacao.repository;

import com.precificacao.precificacao.entity.LojaPlataforma;
import com.precificacao.precificacao.enums.Plataforma;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LojaPlataformaRepository
        extends JpaRepository<LojaPlataforma, Long> {

    Optional<LojaPlataforma> findByLojaIdAndPlataforma(
            Long lojaId,
            Plataforma plataforma
    );

    List<LojaPlataforma> findByLojaIdAndAtivoTrue(
            Long lojaId
    );

    List<LojaPlataforma> findByPlataformaAndAtivoTrue(
            Plataforma plataforma
    );

    boolean existsByLojaIdAndPlataformaAndAtivoTrue(
            Long lojaId,
            Plataforma plataforma
    );

    List<LojaPlataforma> findByLojaId(Long lojaId);
}