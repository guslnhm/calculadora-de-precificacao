package com.precificacao.precificacao.exception;

public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException() {
        super("Usuário ou senha inválidos");
    }
}