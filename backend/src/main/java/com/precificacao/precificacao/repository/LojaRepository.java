package com.precificacao.precificacao.repository;

import com.precificacao.precificacao.entity.Loja;
import com.precificacao.precificacao.enums.Plataforma;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LojaRepository extends JpaRepository<Loja, Long> {

    List<Loja> findByAtivoTrue();

    @Query("""
            SELECT l
            FROM Loja l
            WHERE l.ativo = true
              AND EXISTS (
                  SELECT lp.id
                  FROM LojaPlataforma lp
                  WHERE lp.loja = l
                    AND lp.plataforma = :plataforma
                    AND lp.ativo = true
              )
            ORDER BY l.nome
            """)
    List<Loja> findAtivasByPlataforma(
            @Param("plataforma") Plataforma plataforma
    );
}