package com.precificacao.precificacao.dto;

import com.precificacao.precificacao.enums.Plataforma;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ConfiguracaoPlataformaResponseDTO {

    private Long id;

    private Long lojaId;
    private String nomeLoja;

    private Plataforma plataforma;

    private BigDecimal percentualImposto;
    private BigDecimal percentualCustoFixo;
    private BigDecimal percentualTaxaPlataforma;
    private BigDecimal percentualTaxaFranquia;
    private BigDecimal percentualTaxaTransacao;
    private BigDecimal percentualTaxaAntecipacao;
    private BigDecimal percentualLucro;

    private Boolean ativo;
}