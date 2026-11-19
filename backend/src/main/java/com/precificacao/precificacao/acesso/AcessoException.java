package com.precificacao.precificacao.acesso;

import org.springframework.http.HttpStatus;

/** Erro de acesso com o status HTTP e uma mensagem para mostrar na tela. */
public class AcessoException extends RuntimeException {

    private final HttpStatus status;

    public AcessoException(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static AcessoException invalido(String mensagem) {
        return new AcessoException(HttpStatus.BAD_REQUEST, mensagem);
    }

    public static AcessoException proibido() {
        return new AcessoException(HttpStatus.FORBIDDEN, "Você não tem acesso a isso");
    }
}
