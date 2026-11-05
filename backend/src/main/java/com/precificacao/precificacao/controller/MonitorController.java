package com.precificacao.precificacao.controller;

import com.precificacao.precificacao.ifood.IfoodProperties;
import com.precificacao.precificacao.monitor.MonitorModelos.IncidenteRelatorio;
import com.precificacao.precificacao.monitor.MonitorModelos.LojaMerchant;
import com.precificacao.precificacao.monitor.MonitorProperties;
import com.precificacao.precificacao.monitor.MonitorRepository;
import com.precificacao.precificacao.monitor.MonitorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Consulta do monitor de lojas do iFood (painel, incidentes, histórico, mensagens). */
@RestController
@RequestMapping("/monitor")
public class MonitorController {

    private final MonitorService monitor;
    private final MonitorRepository repo;
    private final MonitorProperties cfg;
    private final Clock clock;
    private final ZoneId zona;

    public MonitorController(MonitorService monitor, MonitorRepository repo, MonitorProperties cfg, Clock clock,
                             IfoodProperties ifood) {
        this.monitor = monitor;
        this.repo = repo;
        this.cfg = cfg;
        this.clock = clock;
        this.zona = ifood.zona();
    }

    /** Situação atual de cada loja/operação, problemas primeiro. */
    @GetMapping("/lojas")
    public List<Map<String, Object>> painel() {
        return repo.painel();
    }

    /** Lojas que autorizaram o aplicativo no iFood (para montar o mapa de grupos). */
    @GetMapping("/lojas-ifood")
    public List<LojaMerchant> lojasIfood() {
        return repo.lojasAtivas();
    }

    /** Incidentes do período (padrão: últimos 7 dias). */
    @GetMapping("/incidentes")
    public List<IncidenteRelatorio> incidentes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) List<String> lojas) {
        LocalDate hoje = LocalDate.now(clock.withZone(zona));
        OffsetDateTime inicio = (de != null ? de : hoje.minusDays(7)).atStartOfDay(zona).toOffsetDateTime();
        OffsetDateTime fim = (ate != null ? ate : hoje).plusDays(1).atStartOfDay(zona).toOffsetDateTime();
        return repo.relatorio(inicio, fim, lojas == null || lojas.isEmpty() ? null : lojas, OffsetDateTime.now(clock));
    }

    /** Cada mudança de status de uma loja (padrão: hoje). */
    @GetMapping("/historico/{merchantId}")
    public List<Map<String, Object>> historico(
            @PathVariable String merchantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        LocalDate hoje = LocalDate.now(clock.withZone(zona));
        return repo.historico(merchantId,
                (de != null ? de : hoje).atStartOfDay(zona).toOffsetDateTime(),
                (ate != null ? ate : hoje).plusDays(1).atStartOfDay(zona).toOffsetDateTime());
    }

    /** Últimas mensagens de WhatsApp (enviadas, com falha ou do modo sombra). */
    @GetMapping("/mensagens")
    public List<Map<String, Object>> mensagens(@RequestParam(defaultValue = "50") int limite) {
        return repo.envios(Math.min(Math.max(limite, 1), 500));
    }

    /** Configuração em uso (sem segredos). */
    @GetMapping("/configuracao")
    public Map<String, Object> configuracao() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ativo", cfg.ativo());
        m.put("enviarWhatsapp", cfg.enviarWhatsapp());
        m.put("intervaloSegundos", cfg.intervaloSegundos());
        m.put("ciclosConfirmacao", cfg.ciclosConfirmacao());
        m.put("estadosQueAlertam", cfg.estadosQueAlertam());
        m.put("lembreteMinutos", cfg.lembreteMinutos());
        m.put("resumoDiario", cfg.resumoDiario());
        return m;
    }

    /** Roda um ciclo agora (mesmo com o monitor automático desligado). */
    @PostMapping("/ciclo")
    public Map<String, Object> rodarAgora() {
        var eventos = monitor.ciclo();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lojas", monitor.lojasMonitoradas().size());
        m.put("eventos", eventos.size());
        return m;
    }

    /** Envia (ou registra, no modo sombra) o resumo de um dia. Padrão: ontem. */
    @PostMapping("/resumo")
    public Map<String, String> resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia) {
        return monitor.enviarResumo(dia);
    }

    /** Mostra na tela o motivo real do erro (em vez de "erro inesperado"). */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> erro(RuntimeException e) {
        org.slf4j.LoggerFactory.getLogger(getClass()).error("Erro no monitor", e);
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("erro", "MONITOR", "mensagem", msg));
    }
}
