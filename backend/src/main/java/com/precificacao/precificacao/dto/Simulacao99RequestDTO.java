package com.precificacao.precificacao.dto;

import com.precificacao.precificacao.enums.FaixaDistancia99;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class Simulacao99RequestDTO {

    @NotNull(message = "O id do item é obrigatório")
    private Long itemId;

    @NotNull(message = "A faixa de distância é obrigatória")
    private FaixaDistancia99 faixaDistancia;

    @NotNull(message = "A taxa 99 é obrigatória")
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal taxa99;

    @NotNull(message = "O imposto é obrigatório")
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal imposto;

    @NotNull(message = "O custo fixo é obrigatório")
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal custoFixo;

    @NotNull(message = "A taxa de franquia é obrigatória")
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal taxaFranquia;

    @NotNull(message = "A taxa de transação é obrigatória")
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal taxaTransacao;

    @NotNull(message = "A taxa de antecipação é obrigatória")
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal taxaAntecipacao;

    @NotNull(message = "O lucro é obrigatório")
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal lucro;

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public FaixaDistancia99 getFaixaDistancia() {
        return faixaDistancia;
    }

    public void setFaixaDistancia(FaixaDistancia99 faixaDistancia) {
        this.faixaDistancia = faixaDistancia;
    }

    public BigDecimal getTaxa99() {
        return taxa99;
    }

    public void setTaxa99(BigDecimal taxa99) {
        this.taxa99 = taxa99;
    }

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

    public BigDecimal getTaxaFranquia() {
        return taxaFranquia;
    }

    public void setTaxaFranquia(BigDecimal taxaFranquia) {
        this.taxaFranquia = taxaFranquia;
    }

    public BigDecimal getTaxaTransacao() {
        return taxaTransacao;
    }

    public void setTaxaTransacao(BigDecimal taxaTransacao) {
        this.taxaTransacao = taxaTransacao;
    }

    public BigDecimal getTaxaAntecipacao() {
        return taxaAntecipacao;
    }

    public void setTaxaAntecipacao(BigDecimal taxaAntecipacao) {
        this.taxaAntecipacao = taxaAntecipacao;
    }

    public BigDecimal getLucro() {
        return lucro;
    }

    public void setLucro(BigDecimal lucro) {
        this.lucro = lucro;
    }
}