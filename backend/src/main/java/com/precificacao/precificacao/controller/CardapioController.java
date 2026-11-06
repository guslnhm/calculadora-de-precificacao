package com.precificacao.precificacao.controller;

import com.precificacao.precificacao.cardapio.CardapioModelos.Cardapio;
import com.precificacao.precificacao.cardapio.CardapioRepository;
import com.precificacao.precificacao.cardapio.CardapioRepository.Alteracao;
import com.precificacao.precificacao.cardapio.CardapioService;
import com.precificacao.precificacao.ifood.IfoodException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cardápio das lojas no iFood: ver, pausar/ativar e mudar preço de itens e complementos. */
@RestController
@RequestMapping("/cardapio")
public class CardapioController {

    public record StatusRequest(String tipo, String id, String status) {
    }

    public record PrecoRequest(String tipo, String id, BigDecimal preco) {
    }

    private final CardapioService service;
    private final CardapioRepository repo;

    public CardapioController(CardapioService service, CardapioRepository repo) {
        this.service = service;
        this.repo = repo;
    }

    /** Lojas com quantos itens têm e quantos estão parados. */
    @GetMapping("/lojas")
    public List<Map<String, Object>> lojas() {
        return repo.resumoLojas();
    }

    /** Itens e complementos parados agora, em todas as lojas. */
    @GetMapping("/pausados")
    public List<Map<String, Object>> pausados() {
        return repo.pausadosAgora();
    }

    /** Cardápio da loja (o salvo; atualizar=true lê do iFood agora). */
    @GetMapping("/{merchantId}")
    public Map<String, Object> cardapio(@PathVariable String merchantId,
                                        @RequestParam(defaultValue = "false") boolean atualizar) {
        Cardapio c = service.cardapio(merchantId, atualizar);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cardapio", c);
        m.put("itens", c.totalItens());
        m.put("pausados", c.totalPausados());
        repo.infoLeitura(merchantId).ifPresent(i -> {
            m.put("erro", i.get("erro"));
            m.put("erroEm", i.get("erro_em"));
        });
        return m;
    }

    @PostMapping("/{merchantId}/status")
    public Alteracao status(@PathVariable String merchantId, @RequestBody StatusRequest req, HttpServletRequest http) {
        return service.alterarStatus(merchantId, req.tipo(), req.id(), req.status(), usuario(http));
    }

    @PostMapping("/{merchantId}/preco")
    public Alteracao preco(@PathVariable String merchantId, @RequestBody PrecoRequest req, HttpServletRequest http) {
        return service.alterarPreco(merchantId, req.tipo(), req.id(), req.preco(), usuario(http));
    }

    @GetMapping("/{merchantId}/alteracoes")
    public List<Alteracao> alteracoes(@PathVariable String merchantId) {
        return service.alteracoes(merchantId);
    }

    @PostMapping("/alteracoes/{id}/desfazer")
    public Alteracao desfazer(@PathVariable long id, HttpServletRequest http) {
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
        LoggerFactory.getLogger(getClass()).warn("iFood recusou: {}", e.getMessage());
        String msg = e.semPermissao()
                ? "O iFood não deixou acessar o cardápio desta loja (sem permissão ou loja não encontrada)."
                : "O iFood respondeu com erro: " + e.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("erro", "IFOOD", "mensagem", msg));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> erro(RuntimeException e) {
        LoggerFactory.getLogger(getClass()).error("Erro no cardápio", e);
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("erro", "CARDAPIO", "mensagem", msg));
    }
}
