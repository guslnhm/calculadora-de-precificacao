package com.precificacao.precificacao.repository;

import com.precificacao.precificacao.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long>{
    //List<Item> findByLojaId(Long lojaId);
    List<Item> findByAtivoTrue();

    List<Item> findByLojaIdAndAtivoTrue(Long lojaId);
}
