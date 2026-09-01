package com.precificacao.precificacao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LojaAtualizacaoDTO {

    @NotBlank(message = "O nome da loja é obrigatório")
    @Size(
            max = 150,
            message = "O nome da loja deve ter no máximo 150 caracteres"
    )
    private String nome;

    private String observacao;
}