package com.precificacao.precificacao.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class SalvarPrecoVendaRequestDTO {

    @NotNull(message = "O preço de venda é obrigatório")
    @DecimalMin(value = "0.0", inclusive = false, message = "O preço de venda deve ser maior que zero")
    private BigDecimal precoVendaAtual;

    public BigDecimal getPrecoVendaAtual() {
        return precoVendaAtual;
    }

    public void setPrecoVendaAtual(BigDecimal precoVendaAtual) {
        this.precoVendaAtual = precoVendaAtual;
    }
}