package com.precificacao.precificacao.exception;

public class PercentualInvalidoException extends RuntimeException {

    public PercentualInvalidoException() {
        super("A soma dos percentuais não pode ser maior ou igual a 100%");
    }
}