package com.precificacao.precificacao.dto;

public class ImportacaoItensResponseDTO {

    private int totalLinhasLidas;
    private int itensImportados;
    private int linhasIgnoradas;
    private int itensAtualizados;
    private String mensagem;

    public int getTotalLinhasLidas() {
        return totalLinhasLidas;
    }

    public void setTotalLinhasLidas(int totalLinhasLidas) {
        this.totalLinhasLidas = totalLinhasLidas;
    }

    public int getItensImportados() {
        return itensImportados;
    }

    public void setItensImportados(int itensImportados) {
        this.itensImportados = itensImportados;
    }

    public int getLinhasIgnoradas() {
        return linhasIgnoradas;
    }

    public void setLinhasIgnoradas(int linhasIgnoradas) {
        this.linhasIgnoradas = linhasIgnoradas;
    }

    public int getItensAtualizados() {
        return itensAtualizados;
    }

    public void setItensAtualizados(int itensAtualizados) {
        this.itensAtualizados = itensAtualizados;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }
}