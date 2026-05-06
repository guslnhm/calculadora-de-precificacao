package com.precificacao.precificacao.dto;

import java.math.BigDecimal;

public class SimulacaoReversaResponseDTO {

    private Long itemId;
    private String nomeItem;
    private Long lojaId;
    private String nomeLoja;

    private BigDecimal cmv;
    private BigDecimal frete;
    private BigDecimal cf;
    private BigDecimal precoVenda;

    private BigDecimal percentualTotal;
    private BigDecimal coeficienteC;
    private BigDecimal custoPercentualReais;

    private BigDecimal lucroReais;
    private BigDecimal lucroPercentual;

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

    public BigDecimal getCf() {
        return cf;
    }

    public void setCf(BigDecimal cf) {
        this.cf = cf;
    }

    public BigDecimal getPrecoVenda() {
        return precoVenda;
    }

    public void setPrecoVenda(BigDecimal precoVenda) {
        this.precoVenda = precoVenda;
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

    public BigDecimal getCustoPercentualReais() {
        return custoPercentualReais;
    }

    public void setCustoPercentualReais(BigDecimal custoPercentualReais) {
        this.custoPercentualReais = custoPercentualReais;
    }

    public BigDecimal getLucroReais() {
        return lucroReais;
    }

    public void setLucroReais(BigDecimal lucroReais) {
        this.lucroReais = lucroReais;
    }

    public BigDecimal getLucroPercentual() {
        return lucroPercentual;
    }

    public void setLucroPercentual(BigDecimal lucroPercentual) {
        this.lucroPercentual = lucroPercentual;
    }
}