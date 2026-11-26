package com.precificacao.precificacao.controller;

import com.precificacao.precificacao.acesso.Sessao;
import com.precificacao.precificacao.painel.PainelRepository;
import jakarta.servlet.http.HttpServletRequest;
import com.precificacao.precificacao.painel.base.BaseHistoricaService;
import com.precificacao.precificacao.painel.base.ConsultaBase;
import com.precificacao.precificacao.painel.base.DestaquesService;
import com.precificacao.precificacao.painel.base.ValoresPortalCsv;
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
    private final DestaquesService destaques;

    public BaseHistoricaController(BaseHistoricaService base, PainelRepository painel, DestaquesService destaques) {
        this.base = base;
        this.painel = painel;
        this.destaques = destaques;
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
        // o que foi lançado antes no boletim deixa de valer: a base (reimportada em seguida) passa a mandar
        String tipo = BaseHistoricaService.tipo(req.periodoTipo());
        LocalDate inicio = BaseHistoricaService.periodo(tipo, req.inicio())[0];
        for (String chave : painel.chavesDaOperacao(req.idOperacao())) {
            painel.apagarManual(chave, tipo, inicio);
        }
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

    /** Planilha com bruto, líquido, recebido via loja e repassado ao iFood por operação e semana. */
    @PostMapping("/valores-portal")
    public Map<String, Object> importarValoresPortal(@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        List<BaseHistoricaService.ValoresPortal> linhas = ValoresPortalCsv.ler(arquivo.getBytes());
        int n = base.importarValoresPortal(linhas);
        // o que foi lançado antes no boletim deixa de valer para esses períodos: a base passa a mandar
        for (BaseHistoricaService.ValoresPortal v : linhas) {
            String tipo = BaseHistoricaService.tipo(v.periodoTipo());
            LocalDate inicio = BaseHistoricaService.periodo(tipo, v.inicio())[0];
            for (String chave : painel.chavesDaOperacao(v.idOperacao())) {
                painel.apagarManual(chave, tipo, inicio);
            }
        }
        return Map.of("linhas", n);
    }

    /** Prévia dos destaques do dia (padrão: ontem): custo do iFood acima de 35% e recordes. */
    @GetMapping("/destaques")
    public Map<String, Object> destaques(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia) {
        LocalDate d = dia != null ? dia : LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).minusDays(1);
        return Map.of("dia", d, "texto", destaques.texto(d));
    }

    /** Envia agora os destaques do dia ao(s) grupo(s) admin. */
    @PostMapping("/destaques/enviar")
    public Map<String, String> enviarDestaques(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia) {
        LocalDate d = dia != null ? dia : LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).minusDays(1);
        return destaques.enviar(d);
    }

    public record PedidoReprocessar(LocalDate de, LocalDate ate) {
    }

    /** Passa de novo para a silver e a gold os pedidos que já estão na base entre as datas. */
    @PostMapping("/reprocessar")
    public BaseHistoricaService.Reprocessado reprocessar(@RequestBody PedidoReprocessar req) {
        return base.reprocessar(req.de(), req.ate());
    }

    public record PedidoConsulta(String sql, Integer limite) {
    }

    @PostMapping("/consulta")
    public ConsultaBase.Resultado consultar(@RequestBody PedidoConsulta req, HttpServletRequest http) {
        Sessao s = Sessao.de(http);
        if (s == null || s.mentorado()) {
            throw new IllegalArgumentException("Sem permissão");
        }
        return base.consultar(req.sql(), req.limite(), s.email());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalido(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("erro", "ARQUIVO_INVALIDO", "mensagem", String.valueOf(e.getMessage())));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> falha(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("erro", "BASE_INDISPONIVEL", "mensagem", e.getMessage()));
    }
}
