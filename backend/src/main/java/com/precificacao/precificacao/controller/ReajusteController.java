package com.precificacao.precificacao.controller;

import com.precificacao.precificacao.cardapio.CardapioService;
import com.precificacao.precificacao.ifood.IfoodException;
import com.precificacao.precificacao.reajuste.ReajusteRepository.LojaIfood;
import com.precificacao.precificacao.reajuste.ReajusteRepository.Lote;
import com.precificacao.precificacao.reajuste.ReajusteService;
import com.precificacao.precificacao.reajuste.ReajusteService.Previa;
import com.precificacao.precificacao.reajuste.ReajusteService.RegrasLoja;
import com.precificacao.precificacao.reajuste.ReajusteService.VinculoRequest;
import com.precificacao.precificacao.reajuste.ReajusteService.VinculosLoja;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reajuste geral de preços no iFood a partir da calculadora. */
@RestController
@RequestMapping("/reajuste")
public class ReajusteController {

    public record RegrasRequest(String modoCupom, String arredondamento, BigDecimal fretePadrao) {
    }

    public record LojasRequest(List<String> merchantIds) {
    }

    public record PublicarRequest(String fonte, List<String> chaves) {
    }

    private final ReajusteService service;

    public ReajusteController(ReajusteService service) {
        this.service = service;
    }

    /** Todas as lojas do iFood e a loja da calculadora de cada uma. */
    @GetMapping("/lojas-ifood")
    public List<LojaIfood> todasLojasIfood() {
        return service.todasLojasIfood();
    }

    @GetMapping("/{lojaId}")
    public Map<String, Object> loja(@PathVariable long lojaId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("regras", service.regras(lojaId));
        m.put("lojasIfood", service.lojasIfood(lojaId));
        return m;
    }

    @PutMapping("/{lojaId}/regras")
    public RegrasLoja regras(@PathVariable long lojaId, @RequestBody RegrasRequest req) {
        return service.salvarRegras(lojaId, req.modoCupom(), req.arredondamento(), req.fretePadrao());
    }

    @PutMapping("/{lojaId}/lojas-ifood")
    public List<LojaIfood> lojasIfood(@PathVariable long lojaId, @RequestBody LojasRequest req) {
        return service.vincularLojas(lojaId, req.merchantIds() == null ? List.of() : req.merchantIds());
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{lojaId}/lojas-ifood/{merchantId}")
    public List<LojaIfood> desligar(@PathVariable long lojaId, @PathVariable String merchantId) {
        return service.desvincularLoja(lojaId, merchantId);
    }

    @GetMapping("/{lojaId}/vinculos/{merchantId}")
    public VinculosLoja vinculos(@PathVariable long lojaId, @PathVariable String merchantId) {
        return service.vinculos(lojaId, merchantId);
    }

    @PutMapping("/{lojaId}/vinculos/{merchantId}")
    public VinculosLoja salvarVinculos(@PathVariable long lojaId, @PathVariable String merchantId,
                                       @RequestBody List<VinculoRequest> req, HttpServletRequest http) {
        service.salvarVinculos(lojaId, merchantId, req, usuario(http));
        return service.vinculos(lojaId, merchantId);
    }

    @PostMapping("/{lojaId}/vinculos/{merchantId}/aceitar-sugestoes")
    public Map<String, Object> aceitar(@PathVariable long lojaId, @PathVariable String merchantId,
                                       @RequestParam(defaultValue = "0.8") double minimo, HttpServletRequest http) {
        int n = service.aceitarSugestoes(lojaId, merchantId, minimo, usuario(http));
        return Map.of("aceitas", n);
    }

    public record ImportarRequest(String merchantId) {
    }

    /** Traz os itens do cardápio do iFood para a calculadora, já ligados. */
    @PostMapping("/{lojaId}/importar-ifood")
    public ReajusteService.Importacao importar(@PathVariable long lojaId, @RequestBody(required = false) ImportarRequest req,
                                               HttpServletRequest http) {
        return service.importarDoIfood(lojaId, req == null ? null : req.merchantId(), usuario(http));
    }

    /** Preço de cada item com cada cupom, para calcular e salvar em massa. */
    @GetMapping("/{lojaId}/precos")
    public ReajusteService.Precos precos(@PathVariable long lojaId) {
        return service.precos(lojaId);
    }

    @PutMapping("/{lojaId}/precos")
    public Map<String, Object> salvarPrecos(@PathVariable long lojaId, @RequestBody List<ReajusteService.SalvarPreco> req) {
        return Map.of("salvos", service.salvarPrecos(lojaId, req));
    }

    @GetMapping("/{lojaId}/previa")
    public Previa previa(@PathVariable long lojaId, @RequestParam(defaultValue = "SALVO") String fonte) {
        return service.previa(lojaId, fonte);
    }

    @PostMapping("/{lojaId}/publicar")
    public Lote publicar(@PathVariable long lojaId, @RequestBody PublicarRequest req, HttpServletRequest http) {
        return service.publicar(lojaId, req.fonte(), req.chaves(), usuario(http));
    }

    @GetMapping("/{lojaId}/lotes")
    public List<Lote> lotes(@PathVariable long lojaId) {
        return service.lotes(lojaId);
    }

    @GetMapping("/lotes/{id}")
    public ResponseEntity<Map<String, Object>> lote(@PathVariable long id) {
        return service.lote(id).map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lote", l);
            m.put("alteracoes", service.alteracoesDoLote(id));
            return ResponseEntity.ok(m);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/lotes/{id}/desfazer")
    public Lote desfazer(@PathVariable long id, HttpServletRequest http) {
        return service.desfazer(id, usuario(http));
    }

    private static String usuario(HttpServletRequest http) {
        Object nome = http.getAttribute("usuarioNome");
        return nome == null ? null : nome.toString();
    }

    @ExceptionHandler(CardapioService.Recusado.class)
    public ResponseEntity<Map<String, Object>> recusado(CardapioService.Recusado e) {
        return ResponseEntity.badRequest().body(Map.of("erro", "RECUSADO", "mensagem", e.getMessage()));
    }

    @ExceptionHandler(IfoodException.class)
    public ResponseEntity<Map<String, Object>> ifood(IfoodException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("erro", "IFOOD", "mensagem", "O iFood respondeu com erro: " + e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> erro(RuntimeException e) {
        LoggerFactory.getLogger(getClass()).error("Erro no reajuste", e);
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("erro", "REAJUSTE", "mensagem", msg));
    }
}
