package com.precificacao.precificacao.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.precificacao.precificacao.acesso.Sessao;
import com.precificacao.precificacao.ifood.IfoodException;
import com.precificacao.precificacao.painel.BiImportService;
import com.precificacao.precificacao.painel.PainelColetaService;
import com.precificacao.precificacao.painel.PainelProperties;
import com.precificacao.precificacao.painel.PainelRepository;
import com.precificacao.precificacao.painel.PainelScheduler;
import com.precificacao.precificacao.painel.PainelService;
import com.precificacao.precificacao.painel.base.BaseHistoricaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Painel de resultados (substitui o Power BI). */
@RestController
@RequestMapping("/painel")
public class PainelController {

    public record ManualRequest(String chave, String periodoTipo, LocalDate inicio, BigDecimal conversao,
                                BigDecimal repasse, BigDecimal valorBruto, BigDecimal valorLiquido,
                                BigDecimal recebidoViaLoja, BigDecimal repassadoPlataforma) {
    }

    public record VinculoRequest(String merchantId) {
    }

    private final PainelService painel;
    private final PainelColetaService coleta;
    private final PainelScheduler agenda;
    private final BiImportService bi;
    private final PainelRepository repo;
    private final PainelProperties cfg;
    private final Clock clock;
    private final BaseHistoricaService base;

    public PainelController(PainelService painel, PainelColetaService coleta, PainelScheduler agenda,
                            BiImportService bi, PainelRepository repo, PainelProperties cfg, Clock clock,
                            BaseHistoricaService base) {
        this.base = base;
        this.painel = painel;
        this.coleta = coleta;
        this.agenda = agenda;
        this.bi = bi;
        this.repo = repo;
        this.cfg = cfg;
        this.clock = clock;
    }

    @GetMapping("/carteira")
    public PainelService.Carteira carteira(@RequestParam(defaultValue = "SEMANAL") String tipo,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                           HttpServletRequest http) {
        Sessao s = Sessao.de(http);
        return painel.carteira(tipo, inicio, s != null && s.mentorado() ? s.lojas() : null);
    }

    @GetMapping("/loja")
    public PainelService.Loja loja(@RequestParam String chave, @RequestParam(defaultValue = "SEMANAL") String tipo,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                   @RequestParam(defaultValue = "12") int quantos) {
        return painel.loja(chave, tipo, inicio, Math.min(Math.max(quantos, 2), 52));
    }

    @PutMapping("/manual")
    public Map<String, Object> manual(@RequestBody ManualRequest req, HttpServletRequest http) {
        if (req.chave() == null || req.chave().isBlank() || req.inicio() == null) {
            throw new IllegalArgumentException("Informe a loja e o período");
        }
        PainelService.Periodo p = painel.periodo(req.periodoTipo(), req.inicio());
        BigDecimal conv = req.conversao();
        if (conv != null && conv.compareTo(BigDecimal.ONE) > 0) {
            conv = conv.movePointLeft(2); // aceita 19,2 (%) ou 0,192
        }
        // valores do Portal: repasse = líquido + recebido via loja; em branco com bruto/líquido preenchidos vale 0
        boolean temValores = req.valorBruto() != null || req.valorLiquido() != null;
        BigDecimal recebido = req.recebidoViaLoja() == null && temValores ? BigDecimal.ZERO : req.recebidoViaLoja();
        BigDecimal repassado = req.repassadoPlataforma() == null && temValores ? BigDecimal.ZERO : req.repassadoPlataforma();
        BigDecimal repasse = req.valorLiquido() != null ? req.valorLiquido().add(recebido) : req.repasse();
        Sessao s = Sessao.de(http);
        Object usuario = s != null ? s.nome() : http.getAttribute("usuarioNome");
        repo.salvarManual(new PainelRepository.Manual(req.chave(), p.tipo(), p.inicio(), conv, repasse,
                OffsetDateTime.now(clock), usuario == null ? null : usuario.toString(), req.valorBruto(),
                req.valorLiquido(), recebido, repassado));

        // também na base histórica, quando a loja é uma operação só da base
        String naBase = "não configurada";
        if (cfg.bancoBiConfigurado()) {
            List<Integer> ops = repo.operacoesDaChave(req.chave());
            if (ops.size() == 1) {
                try {
                    base.salvarValoresPortal(ops.get(0), p.tipo(), p.inicio(), conv, req.valorBruto(),
                            req.valorLiquido(), recebido, repassado);
                    naBase = "gravado";
                } catch (RuntimeException e) {
                    naBase = "erro: " + e.getMessage();
                }
            } else {
                naBase = ops.isEmpty() ? "loja sem operação na base" : "loja com mais de uma operação na base";
            }
        }
        return Map.of("ok", true, "periodo", p, "base", naBase);
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("analyticsConfigurado", cfg.analyticsConfigurado());
        m.put("bancoBiConfigurado", cfg.bancoBiConfigurado());
        m.put("coletaAtiva", cfg.coletaAtiva());
        m.put("coletando", coleta.rodando());
        m.put("ultimaColeta", coleta.ultimaExecucao());
        m.put("biImportadoEm", repo.biImportadoEm());
        m.put("lojas", repo.situacaoColeta());
        return m;
    }

    @PostMapping("/coletar")
    public Map<String, Object> coletar() {
        if (!cfg.analyticsConfigurado()) {
            throw new IllegalArgumentException("Configure IFOOD_BI_CLIENT_ID e IFOOD_BI_CLIENT_SECRET no Railway");
        }
        boolean iniciou = agenda.dispararColeta();
        return Map.of("iniciou", iniciou, "mensagem", iniciou ? "Coleta iniciada" : "Já existe uma coleta em andamento");
    }

    @PostMapping("/coletar/{merchantId}")
    public Map<String, Object> coletarLoja(@PathVariable String merchantId) {
        boolean ok = coleta.coletarLoja(merchantId);
        return Map.of("ok", ok);
    }

    @PostMapping("/importar-bi")
    public BiImportService.Resultado importarBi() {
        return bi.importar();
    }

    @GetMapping("/operacoes-bi")
    public List<PainelRepository.OperacaoBi> operacoesBi() {
        return repo.operacoesBi();
    }

    @PutMapping("/operacoes-bi/{idOperacao}")
    public Map<String, Object> vincular(@PathVariable int idOperacao, @RequestBody VinculoRequest req) {
        String m = req.merchantId() == null || req.merchantId().isBlank() ? null : req.merchantId();
        repo.vincular(idOperacao, m, m == null ? "NENHUM" : "MANUAL", OffsetDateTime.now(clock));
        return Map.of("ok", true);
    }

    /** Resposta bruta do Analytics para uma loja (conferência). */
    @GetMapping("/diagnostico/{merchantId}")
    public JsonNode diagnostico(@PathVariable String merchantId,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return coleta.diagnostico(merchantId, de, ate);
    }

    @ExceptionHandler(IfoodException.class)
    public ResponseEntity<Map<String, Object>> erroIfood(IfoodException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> erroRequisicao(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
    }
}
