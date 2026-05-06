package com.precificacao.precificacao.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class SimulacaoReversaRequestDTO {

    @NotNull(message = "O id do item é obrigatório")
    private Long itemId;

    @NotNull(message = "O preço de venda é obrigatório")
    @DecimalMin(value = "0.0", inclusive = false, message = "O preço de venda deve ser maior que zero")
    private BigDecimal precoVenda;

    @NotNull(message = "O frete é obrigatório")
    @DecimalMin(value = "0.0", inclusive = true, message = "O frete não pode ser negativo")
    private BigDecimal frete;

    @NotNull(message = "O imposto é obrigatório")
    @DecimalMin(value = "0.0", inclusive = true, message = "O imposto não pode ser negativo")
    private BigDecimal imposto;

    @NotNull(message = "As taxas de franquia são obrigatórias")
    @DecimalMin(value = "0.0", inclusive = true, message = "As taxas de franquia não podem ser negativas")
    private BigDecimal taxaFranquia;

    @NotNull(message = "O custo fixo é obrigatório")
    @DecimalMin(value = "0.0", inclusive = true, message = "O custo fixo não pode ser negativo")
    private BigDecimal custoFixo;

    @NotNull(message = "A taxa de transação é obrigatória")
    @DecimalMin(value = "0.0", inclusive = true, message = "A taxa de transação não pode ser negativa")
    private BigDecimal taxaTransacao;

    @NotNull(message = "A taxa iFood é obrigatória")
    @DecimalMin(value = "0.0", inclusive = true, message = "A taxa iFood não pode ser negativa")
    private BigDecimal taxaIfood;

    @NotNull(message = "A taxa de antecipação é obrigatória")
    @DecimalMin(value = "0.0", inclusive = true, message = "A taxa de antecipação não pode ser negativa")
    private BigDecimal taxaRepasse;

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public BigDecimal getPrecoVenda() {
        return precoVenda;
    }

    public void setPrecoVenda(BigDecimal precoVenda) {
        this.precoVenda = precoVenda;
    }

    public BigDecimal getFrete() {
        return frete;
    }

    public void setFrete(BigDecimal frete) {
        this.frete = frete;
    }

    public BigDecimal getImposto() {
        return imposto;
    }

    public void setImposto(BigDecimal imposto) {
        this.imposto = imposto;
    }

    public BigDecimal getTaxaFranquia() {
        return taxaFranquia;
    }

    public void setTaxaFranquia(BigDecimal taxaFranquia) {
        this.taxaFranquia = taxaFranquia;
    }

    public BigDecimal getCustoFixo() {
        return custoFixo;
    }

    public void setCustoFixo(BigDecimal custoFixo) {
        this.custoFixo = custoFixo;
    }

    public BigDecimal getTaxaTransacao() {
        return taxaTransacao;
    }

    public void setTaxaTransacao(BigDecimal taxaTransacao) {
        this.taxaTransacao = taxaTransacao;
    }

    public BigDecimal getTaxaIfood() {
        return taxaIfood;
    }

    public void setTaxaIfood(BigDecimal taxaIfood) {
        this.taxaIfood = taxaIfood;
    }

    public BigDecimal getTaxaRepasse() {
        return taxaRepasse;
    }

    public void setTaxaRepasse(BigDecimal taxaRepasse) {
        this.taxaRepasse = taxaRepasse;
    }
}