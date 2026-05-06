package com.precificacao.precificacao.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public class LojaPercentuaisDTO {

    @DecimalMin(value = "0.0", inclusive = true, message = "Imposto não pode ser negativo")
    private BigDecimal imposto;

    @DecimalMin(value = "0.0", inclusive = true, message = "Custo fixo não pode ser negativo")
    private BigDecimal custoFixo;

    @DecimalMin(value = "0.0", inclusive = true, message = "Taxa de transação não pode ser negativa")
    private BigDecimal taxaTransacao;

    @DecimalMin(value = "0.0", inclusive = true, message = "Taxa iFood não pode ser negativa")
    private BigDecimal taxaIfood;

    @DecimalMin(value = "0.0", inclusive = true, message = "Taxa de repasse não pode ser negativa")
    private BigDecimal taxaRepasse;

    @DecimalMin(value = "0.0", inclusive = true, message = "Lucro não pode ser negativo")
    private BigDecimal lucro;

    public BigDecimal getImposto() {
        return imposto;
    }

    public void setImposto(BigDecimal imposto) {
        this.imposto = imposto;
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

    public BigDecimal getLucro() {
        return lucro;
    }

    public void setLucro(BigDecimal lucro) {
        this.lucro = lucro;
    }

    @DecimalMin(value = "0.0", inclusive = true, message = "Taxas de franquia não podem ser negativas")
    private BigDecimal taxaFranquia;

    public BigDecimal getTaxaFranquia() {
        return taxaFranquia;
    }

    public void setTaxaFranquia(BigDecimal taxaFranquia) {
        this.taxaFranquia = taxaFranquia;
    }

}