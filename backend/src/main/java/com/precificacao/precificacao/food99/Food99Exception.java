package com.precificacao.precificacao.food99;

public class Food99Exception extends RuntimeException {

    private final int errno;

    public Food99Exception(int errno, String mensagem) {
        super(mensagem);
        this.errno = errno;
    }

    public Food99Exception(String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.errno = -1;
    }

    public int errno() {
        return errno;
    }
}
