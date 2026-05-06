package com.precificacao.precificacao.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ItemNaoEncontradoException.class)
    public ResponseEntity<?> handleItemNaoEncontrado(ItemNaoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "erro", "ITEM_NAO_ENCONTRADO",
                        "mensagem", ex.getMessage()
                ));
    }

    @ExceptionHandler(PercentualInvalidoException.class)
    public ResponseEntity<?> handlePercentualInvalido(PercentualInvalidoException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "erro", "PERCENTUAL_INVALIDO",
                        "mensagem", ex.getMessage()
                ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "erro", "RECURSO_NAO_ENCONTRADO",
                        "mensagem", "A rota informada não existe"
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleErroGenerico(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "erro", "ERRO_INTERNO",
                        "mensagem", "Ocorreu um erro inesperado no servidor"
                ));
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<?> handleCredenciaisInvalidas(CredenciaisInvalidasException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "erro", "CREDENCIAIS_INVALIDAS",
                        "mensagem", ex.getMessage()
                ));
    }
}