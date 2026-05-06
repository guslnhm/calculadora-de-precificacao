package com.precificacao.precificacao.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class ItemRequestDTO {

    @NotNull(message = "O id da loja é obrigatório")
    private Long lojaId;

    @NotBlank(message = "O nome do item é obrigatório")
    @Size(max = 150, message = "O nome do item deve ter no máximo 150 caracteres")
    private String nomeItem;

    @NotNull(message = "O CMV é obrigatório")
    @DecimalMin(value = "0.0", inclusive = true, message = "O CMV não pode ser negativo")
    private BigDecimal cmv;

    @DecimalMin(value = "0.0", inclusive = true, message = "O preço de venda inicial não pode ser negativo")
    private BigDecimal precoVendaInicial;

    @DecimalMin(value = "0.0", inclusive = true, message = "O rendimento não pode ser negativo")
    private BigDecimal rendimento;

    private String observacao;

    public Long getLojaId() {
        return lojaId;
    }

    public void setLojaId(Long lojaId) {
        this.lojaId = lojaId;
    }

    public String getNomeItem() {
        return nomeItem;
    }

    public void setNomeItem(String nomeItem) {
        this.nomeItem = nomeItem;
    }

    public BigDecimal getPrecoVendaInicial() {
        return precoVendaInicial;
    }

    public void setPrecoVendaInicial(BigDecimal precoVendaInicial) {
        this.precoVendaInicial = precoVendaInicial;
    }

    public BigDecimal getRendimento() {
        return rendimento;
    }

    public void setRendimento(BigDecimal rendimento) {
        this.rendimento = rendimento;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public BigDecimal getCmv() {
        return cmv;
    }

    public void setCmv(BigDecimal cmv) {
        this.cmv = cmv;
    }

}