package com.precificacao.precificacao.controller;

import com.precificacao.precificacao.painel.PainelRepository;
import com.precificacao.precificacao.painel.base.BaseHistoricaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Base histórica (banco "pedidos"): envio do relatório de pedidos do Portal. Só equipe. */
@RestController
@RequestMapping("/painel/base")
public class BaseHistoricaController {

    private final BaseHistoricaService base;
    private final PainelRepository painel;

    public BaseHistoricaController(BaseHistoricaService base, PainelRepository painel) {
        this.base = base;
        this.painel = painel;
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

    /** Métricas manuais (bruto, líquido, conversão, alteração de cardápio…) das operações no período. */
    @GetMapping("/metricas")
    public List<BaseHistoricaService.Metrica> metricas(@RequestParam(defaultValue = "SEMANAL") String tipo,
                                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio) {
        Map<Integer, String> merchant = new HashMap<>();
        for (PainelRepository.OperacaoBi o : painel.operacoesBi()) {
            if (o.merchantId() != null) {
                merchant.put(o.idOperacao(), o.merchantId());
            }
        }
        return base.metricas(tipo, inicio, merchant);
    }

    @PutMapping("/metricas")
    public Map<String, Object> salvarMetrica(@RequestBody BaseHistoricaService.EdicaoMetrica req) {
        base.salvarMetrica(req);
        return Map.of("ok", true);
    }

    @GetMapping("/sem-operacao")
    public List<BaseHistoricaService.LojaSemOperacao> semOperacao() {
        return base.semOperacao();
    }

    @GetMapping("/operacoes")
    public List<BaseHistoricaService.Operacao> operacoes() {
        return base.operacoes();
    }

    @PostMapping("/operacoes")
    public Map<String, Object> cadastrarOperacao(@RequestBody BaseHistoricaService.NovaOperacao req) {
        return Map.of("pedidos", base.cadastrarOperacao(req));
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
