package com.precificacao.precificacao.dto;

import com.precificacao.precificacao.enums.Plataforma;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class LojaPlataformasResponseDTO {

    private Long lojaId;
    private String nomeLoja;
    private Set<Plataforma> plataformas;
}