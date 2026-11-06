package com.precificacao.precificacao.controller;

import com.precificacao.precificacao.ifood.IfoodException;
import com.precificacao.precificacao.lojaifood.LojaIfoodService;
import com.precificacao.precificacao.lojaifood.LojaIfoodService.Faixa;
import com.precificacao.precificacao.lojaifood.LojaIfoodService.Loja;
import com.precificacao.precificacao.lojaifood.LojaIfoodService.Pausa;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Loja no iFood pelo site: informações, pausas e horários (API Merchant, ao vivo). */
@RestController
@RequestMapping("/ifood/lojas")
public class LojaIfoodController {

    public record PausarRequest(Integer minutos, String descricao) {
    }

    private final LojaIfoodService service;

    public LojaIfoodController(LojaIfoodService service) {
        this.service = service;
    }

    @GetMapping
    public List<Loja> lojas() {
        return service.lojas();
    }

    @GetMapping("/{merchantId}")
    public Map<String, Object> informacoes(@PathVariable String merchantId) {
        return service.informacoes(merchantId);
    }

    @GetMapping("/{merchantId}/pausas")
    public List<Pausa> pausas(@PathVariable String merchantId) {
        return service.pausas(merchantId);
    }

    @PostMapping("/{merchantId}/pausas")
    public Map<String, Object> pausar(@PathVariable String merchantId, @RequestBody PausarRequest req) {
        return service.pausar(merchantId, req.minutos() == null ? 30 : req.minutos(), req.descricao());
    }

    /** Remove uma pausa pelo ID. */
    @DeleteMapping("/{merchantId}/pausas/{pausaId}")
    public Map<String, Object> removerPausa(@PathVariable String merchantId, @PathVariable String pausaId) {
        return Map.of("removidas", service.removerPausa(merchantId, pausaId));
    }

    /** Remove todas as pausas ativas. */
    @DeleteMapping("/{merchantId}/pausas")
    public Map<String, Object> removerPausas(@PathVariable String merchantId) {
        return Map.of("removidas", service.removerPausa(merchantId, null));
    }

    @GetMapping("/{merchantId}/horarios")
    public Map<String, List<Faixa>> horarios(@PathVariable String merchantId) {
        return service.horarios(merchantId);
    }

    @PutMapping("/{merchantId}/horarios")
    public Map<String, List<Faixa>> definirHorarios(@PathVariable String merchantId,
                                                    @RequestBody Map<String, List<Faixa>> dias) {
        return service.definirHorarios(merchantId, dias);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> invalido(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("erro", "INVALIDO", "mensagem", e.getMessage()));
    }

    @ExceptionHandler(IfoodException.class)
    public ResponseEntity<Map<String, Object>> ifood(IfoodException e) {
        LoggerFactory.getLogger(getClass()).warn("iFood recusou: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("erro", "IFOOD", "mensagem", "O iFood respondeu com erro: " + e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> erro(RuntimeException e) {
        LoggerFactory.getLogger(getClass()).error("Erro na loja iFood", e);
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("erro", "LOJA", "mensagem", msg));
    }
}
