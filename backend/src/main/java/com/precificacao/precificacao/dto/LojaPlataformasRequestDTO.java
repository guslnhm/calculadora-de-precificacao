package com.precificacao.precificacao.dto;

import com.precificacao.precificacao.enums.Plataforma;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class LojaPlataformasRequestDTO {

    @NotEmpty(message = "Selecione pelo menos uma plataforma")
    private Set<Plataforma> plataformas;
}