package com.precificacao.precificacao.ifood;

/** Erro numa chamada ao iFood. status 0 = falha de rede (sem resposta). */
public class IfoodException extends RuntimeException {

    private final int status;
    private final String corpo;

    public IfoodException(int status, String mensagem, String corpo) {
        super(status == 0 ? mensagem : "HTTP " + status + ": " + mensagem);
        this.status = status;
        this.corpo = corpo;
    }

    public IfoodException(String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.status = 0;
        this.corpo = null;
    }

    public int getStatus() {
        return status;
    }

    /** Corpo da resposta do iFood (costuma explicar o erro). */
    public String getCorpo() {
        return corpo;
    }

    /** 403/404: a loja revogou a permissão do aplicativo ou o ID está errado. */
    public boolean semPermissao() {
        return status == 403 || status == 404;
    }
}
