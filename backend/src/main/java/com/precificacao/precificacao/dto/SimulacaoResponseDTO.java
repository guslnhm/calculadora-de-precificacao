package com.precificacao.precificacao.dto;

import java.math.BigDecimal;

public class SimulacaoResponseDTO {

    private Long itemId;
    private String nomeItem;
    private Long lojaId;
    private String nomeLoja;

    private BigDecimal cmv;
    private BigDecimal frete;
    private BigDecimal cmvMaisFrete;

    private BigDecimal percentualTotal;
    private BigDecimal coeficienteC;

    private BigDecimal valorBase;
    private BigDecimal valorCupom5;
    private BigDecimal valorCupom8;
    private BigDecimal valorCupom10;

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getNomeItem() {
        return nomeItem;
    }

    public void setNomeItem(String nomeItem) {
        this.nomeItem = nomeItem;
    }

    public Long getLojaId() {
        return lojaId;
    }

    public void setLojaId(Long lojaId) {
        this.lojaId = lojaId;
    }

    public String getNomeLoja() {
        return nomeLoja;
    }

    public void setNomeLoja(String nomeLoja) {
        this.nomeLoja = nomeLoja;
    }

    public BigDecimal getCmv() {
        return cmv;
    }

    public void setCmv(BigDecimal cmv) {
        this.cmv = cmv;
    }

    public BigDecimal getFrete() {
        return frete;
    }

    public void setFrete(BigDecimal frete) {
        this.frete = frete;
    }

    public BigDecimal getCmvMaisFrete() {
        return cmvMaisFrete;
    }

    public void setCmvMaisFrete(BigDecimal cmvMaisFrete) {
        this.cmvMaisFrete = cmvMaisFrete;
    }

    public BigDecimal getPercentualTotal() {
        return percentualTotal;
    }

    public void setPercentualTotal(BigDecimal percentualTotal) {
        this.percentualTotal = percentualTotal;
    }

    public BigDecimal getCoeficienteC() {
        return coeficienteC;
    }

    public void setCoeficienteC(BigDecimal coeficienteC) {
        this.coeficienteC = coeficienteC;
    }

    public BigDecimal getValorBase() {
        return valorBase;
    }

    public void setValorBase(BigDecimal valorBase) {
        this.valorBase = valorBase;
    }

    public BigDecimal getValorCupom5() {
        return valorCupom5;
    }

    public void setValorCupom5(BigDecimal valorCupom5) {
        this.valorCupom5 = valorCupom5;
    }

    public BigDecimal getValorCupom8() {
        return valorCupom8;
    }

    public void setValorCupom8(BigDecimal valorCupom8) {
        this.valorCupom8 = valorCupom8;
    }

    public BigDecimal getValorCupom10() {
        return valorCupom10;
    }

    public void setValorCupom10(BigDecimal valorCupom10) {
        this.valorCupom10 = valorCupom10;
    }
}