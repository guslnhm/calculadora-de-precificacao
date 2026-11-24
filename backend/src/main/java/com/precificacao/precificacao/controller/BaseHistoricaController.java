package com.precificacao.precificacao.controller;

import com.precificacao.precificacao.painel.base.BaseHistoricaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/** Base histórica (banco "pedidos"): envio do relatório de pedidos do Portal. Só equipe. */
@RestController
@RequestMapping("/painel/base")
public class BaseHistoricaController {

    private final BaseHistoricaService base;

    public BaseHistoricaController(BaseHistoricaService base) {
        this.base = base;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("configurada", base.configurada());
    }

    @PostMapping("/relatorio")
    public BaseHistoricaService.Resultado enviar(@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Escolha o arquivo do relatório");
        }
        return base.enviar(arquivo.getOriginalFilename(), arquivo.getBytes());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalido(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("erro", "ARQUIVO_INVALIDO", "mensagem", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> falha(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("erro", "BASE_INDISPONIVEL", "mensagem", e.getMessage()));
    }
}
