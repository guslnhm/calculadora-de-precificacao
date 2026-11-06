package com.precificacao.precificacao.repository;

import com.precificacao.precificacao.entity.LojaIfood;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LojaIfoodRepository extends JpaRepository<LojaIfood, Long> {

    List<LojaIfood> findByLojaId(Long lojaId);

    Optional<LojaIfood> findByMerchantId(String merchantId);

    List<LojaIfood> findByAtivoTrue();
}
