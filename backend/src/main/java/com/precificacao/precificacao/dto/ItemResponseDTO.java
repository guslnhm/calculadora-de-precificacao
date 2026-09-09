package com.precificacao.precificacao.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ItemResponseDTO {

    private Long id;
    private Long lojaId;
    private String nomeLoja;
    private String nomeItem;
    private BigDecimal cmv;
    private BigDecimal precoVendaInicial;
    private BigDecimal rendimento;
    private String observacao;
    private BigDecimal precoVendaAtual;
    private BigDecimal precoVendaAtualIfood;
    private BigDecimal precoVendaAtual99Food;
    private BigDecimal cmvPercentualIfood;
    private BigDecimal cmvPercentual99Food;
    private LocalDateTime dataPrecificacao;
    private Boolean ativo;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
    private BigDecimal lucratividadeIfood;
    private BigDecimal lucratividade99Food;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public BigDecimal getPrecoVendaAtual() {
        return precoVendaAtual;
    }

    public void setPrecoVendaAtual(BigDecimal precoVendaAtual) {
        this.precoVendaAtual = precoVendaAtual;
    }

    public BigDecimal getPrecoVendaAtualIfood() {
        return precoVendaAtualIfood;
    }

    public void setPrecoVendaAtualIfood(BigDecimal precoVendaAtualIfood) {
        this.precoVendaAtualIfood = precoVendaAtualIfood;
    }

    public BigDecimal getPrecoVendaAtual99Food() {
        return precoVendaAtual99Food;
    }

    public void setPrecoVendaAtual99Food(BigDecimal precoVendaAtual99Food) {
        this.precoVendaAtual99Food = precoVendaAtual99Food;
    }

    public BigDecimal getCmvPercentualIfood() {
        return cmvPercentualIfood;
    }

    public void setCmvPercentualIfood(BigDecimal cmvPercentualIfood) {
        this.cmvPercentualIfood = cmvPercentualIfood;
    }

    public BigDecimal getCmvPercentual99Food() {
        return cmvPercentual99Food;
    }

    public void setCmvPercentual99Food(BigDecimal cmvPercentual99Food) {
        this.cmvPercentual99Food = cmvPercentual99Food;
    }

    public LocalDateTime getDataPrecificacao() {
        return dataPrecificacao;
    }

    public void setDataPrecificacao(LocalDateTime dataPrecificacao) {
        this.dataPrecificacao = dataPrecificacao;
    }

    public Boolean getAtivo() {
        return ativo;
    }

    public void setAtivo(Boolean ativo) {
        this.ativo = ativo;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(LocalDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }

    public BigDecimal getCmv() {
        return cmv;
    }

    public void setCmv(BigDecimal cmv) {
        this.cmv = cmv;
    }

    public BigDecimal getLucratividadeIfood() {
        return lucratividadeIfood;
    }

    public void setLucratividadeIfood(BigDecimal lucratividadeIfood) {
        this.lucratividadeIfood = lucratividadeIfood;
    }

    public BigDecimal getLucratividade99Food() {
        return lucratividade99Food;
    }

    public void setLucratividade99Food(BigDecimal lucratividade99Food) {
        this.lucratividade99Food = lucratividade99Food;
    }
}