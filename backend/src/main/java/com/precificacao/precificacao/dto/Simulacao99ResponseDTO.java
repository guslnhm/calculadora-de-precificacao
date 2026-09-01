package com.precificacao.precificacao.dto;

import com.precificacao.precificacao.enums.FaixaDistancia99;

import java.math.BigDecimal;

public class Simulacao99ResponseDTO {

    private Long itemId;
    private String nomeItem;

    private Long lojaId;
    private String nomeLoja;

    private BigDecimal cmv;

    private FaixaDistancia99 faixaDistancia;
    private BigDecimal custoLogistico;

    private BigDecimal percentualTotal;
    private BigDecimal coeficiente;

    private BigDecimal valorPrato;
    private BigDecimal valorFreteGratis;

    private BigDecimal valor20Off;
    private BigDecimal valor30Off;
    private BigDecimal valor40Off;
    private BigDecimal valor50Off;
    private BigDecimal valor60Off;

    private BigDecimal valor30OffCoparticipacao;
    private BigDecimal valor40OffCoparticipacao;
    private BigDecimal valor50OffCoparticipacao;

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

    public FaixaDistancia99 getFaixaDistancia() {
        return faixaDistancia;
    }

    public void setFaixaDistancia(FaixaDistancia99 faixaDistancia) {
        this.faixaDistancia = faixaDistancia;
    }

    public BigDecimal getCustoLogistico() {
        return custoLogistico;
    }

    public void setCustoLogistico(BigDecimal custoLogistico) {
        this.custoLogistico = custoLogistico;
    }

    public BigDecimal getPercentualTotal() {
        return percentualTotal;
    }

    public void setPercentualTotal(BigDecimal percentualTotal) {
        this.percentualTotal = percentualTotal;
    }

    public BigDecimal getCoeficiente() {
        return coeficiente;
    }

    public void setCoeficiente(BigDecimal coeficiente) {
        this.coeficiente = coeficiente;
    }

    public BigDecimal getValorPrato() {
        return valorPrato;
    }

    public void setValorPrato(BigDecimal valorPrato) {
        this.valorPrato = valorPrato;
    }

    public BigDecimal getValorFreteGratis() {
        return valorFreteGratis;
    }

    public void setValorFreteGratis(BigDecimal valorFreteGratis) {
        this.valorFreteGratis = valorFreteGratis;
    }

    public BigDecimal getValor20Off() {
        return valor20Off;
    }

    public void setValor20Off(BigDecimal valor20Off) {
        this.valor20Off = valor20Off;
    }

    public BigDecimal getValor30Off() {
        return valor30Off;
    }

    public void setValor30Off(BigDecimal valor30Off) {
        this.valor30Off = valor30Off;
    }

    public BigDecimal getValor40Off() {
        return valor40Off;
    }

    public void setValor40Off(BigDecimal valor40Off) {
        this.valor40Off = valor40Off;
    }

    public BigDecimal getValor50Off() {
        return valor50Off;
    }

    public void setValor50Off(BigDecimal valor50Off) {
        this.valor50Off = valor50Off;
    }

    public BigDecimal getValor60Off() {
        return valor60Off;
    }

    public void setValor60Off(BigDecimal valor60Off) {
        this.valor60Off = valor60Off;
    }

    public BigDecimal getValor30OffCoparticipacao() {
        return valor30OffCoparticipacao;
    }

    public void setValor30OffCoparticipacao(BigDecimal valor30OffCoparticipacao) {
        this.valor30OffCoparticipacao = valor30OffCoparticipacao;
    }

    public BigDecimal getValor40OffCoparticipacao() {
        return valor40OffCoparticipacao;
    }

    public void setValor40OffCoparticipacao(BigDecimal valor40OffCoparticipacao) {
        this.valor40OffCoparticipacao = valor40OffCoparticipacao;
    }

    public BigDecimal getValor50OffCoparticipacao() {
        return valor50OffCoparticipacao;
    }

    public void setValor50OffCoparticipacao(BigDecimal valor50OffCoparticipacao) {
        this.valor50OffCoparticipacao = valor50OffCoparticipacao;
    }
}