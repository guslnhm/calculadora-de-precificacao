package com.precificacao.precificacao.dto;

public class ImportacaoItensResponseDTO {

    private int totalLinhasLidas;
    private int itensImportados;
    private int linhasIgnoradas;
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

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }
}