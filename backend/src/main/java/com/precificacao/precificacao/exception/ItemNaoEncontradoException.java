package com.precificacao.precificacao.exception;

public class ItemNaoEncontradoException extends RuntimeException {

    public ItemNaoEncontradoException(Long id) {
        super("Item com id " + id + " não foi encontrado");
    }
}