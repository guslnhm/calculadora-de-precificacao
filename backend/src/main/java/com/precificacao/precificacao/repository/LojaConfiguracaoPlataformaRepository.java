package com.precificacao.precificacao.repository;

import com.precificacao.precificacao.entity.LojaConfiguracaoPlataforma;
import com.precificacao.precificacao.enums.Plataforma;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LojaConfiguracaoPlataformaRepository
        extends JpaRepository<LojaConfiguracaoPlataforma, Long> {

    Optional<LojaConfiguracaoPlataforma> findByLojaIdAndPlataforma(
            Long lojaId,
            Plataforma plataforma
    );
}