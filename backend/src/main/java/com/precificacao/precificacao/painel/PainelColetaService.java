package com.precificacao.precificacao.painel;

import com.fasterxml.jackson.databind.JsonNode;
import com.precificacao.precificacao.ifood.IfoodException;
import com.precificacao.precificacao.ifood.IfoodProperties;
import com.precificacao.precificacao.painel.LeitorKpis.Dia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coleta diária do Analytics (D-1): números por dia de cada loja e a curva de venda por hora.
 *
 * - Na 1ª coleta de uma loja, busca dias-historico dias para trás, em blocos de até 30 dias.
 * - Depois, relê os últimos dias-reprocessar dias (cancelamentos chegam depois) até ontem.
 * - A curva (venda média por dia da semana e hora) é refeita uma vez por semana.
 * - Loja que não autorizou o app de BI devolve 403: fica registrado e a coleta segue.
 */
@Service
public class PainelColetaService {

    private static final Logger log = LoggerFactory.getLogger(PainelColetaService.class);
    static final int BLOCO_DIAS = 30;

    private final LeitorKpis.Fonte fonte;
    private final PainelRepository repo;
    private final PainelProperties cfg;
    private final Clock clock;
    private final ZoneId zona;
    private final AtomicBoolean rodando = new AtomicBoolean(false);
    private volatile String ultimaExecucao;
    private volatile OffsetDateTime terminouEm;

    /** Duas últimas leituras do dia de ontem (pedidos + cancelados por loja), para saber se o iFood parou de mudar. */
    private record Foto(LocalDate dia, Map<String, Integer> movimento, OffsetDateTime em) {
    }

    private volatile Foto fotoAnterior;
    private volatile Foto fotoAtual;

    @org.springframework.beans.factory.annotation.Autowired
    public PainelColetaService(AnalyticsClient fonte, PainelRepository repo, PainelProperties cfg, Clock clock,
                               IfoodProperties ifood) {
        this((LeitorKpis.Fonte) fonte, repo, cfg, clock, ifood.zona());
    }

    public PainelColetaService(LeitorKpis.Fonte fonte, PainelRepository repo, PainelProperties cfg, Clock clock,
                               ZoneId zona) {
        this.fonte = fonte;
        this.repo = repo;
        this.cfg = cfg;
        this.clock = clock;
        this.zona = zona;
    }

    public boolean rodando() {
        return rodando.get();
    }

    public OffsetDateTime terminouEm() {
        return terminouEm;
    }

    public String ultimaExecucao() {
        return ultimaExecucao;
    }

    /** Coleta todas as lojas ativas. Devolve false se já havia uma coleta em andamento. */
    public boolean coletarTodas() {
        if (!rodando.compareAndSet(false, true)) {
            return false;
        }
        int ok = 0, erro = 0;
        try {
            for (String m : repo.lojasAtivas()) {
                if (coletarLoja(m)) {
                    ok++;
                } else {
                    erro++;
                }
            }
        } finally {
            terminouEm = agora();
            fotografar(ontem());
            rodando.set(false);
            ultimaExecucao = agora() + ": " + ok + " loja(s) ok, " + erro + " com erro";
            log.info("[painel] coleta: {}", ultimaExecucao);
        }
        return true;
    }

    /**
     * Relê só um dia de todas as lojas (o iFood libera o D-1 aos poucos depois das 9h).
     * Devolve false se já havia uma coleta em andamento.
     */
    public boolean recoletarDia(LocalDate dia) {
        if (!rodando.compareAndSet(false, true)) {
            return false;
        }
        int erro = 0;
        try {
            for (String m : repo.lojasAtivas()) {
                try {
                    coletarBloco(m, dia, dia);
                } catch (RuntimeException e) {
                    erro++;
                }
            }
        } finally {
            Foto antes = fotoAtual;
            fotografar(dia);
            rodando.set(false);
            boolean mudou = antes != null && antes.dia().equals(dia) && !antes.movimento().equals(fotoAtual.movimento());
            log.info("[painel] releitura de {}: {} ({} loja(s) com erro)", dia, mudou ? "números mudaram" : "sem mudança", erro);
        }
        return true;
    }

    private void fotografar(LocalDate dia) {
        fotoAnterior = fotoAtual;
        fotoAtual = new Foto(dia, repo.movimentoPorLoja(dia), agora());
    }

    /** As duas últimas leituras do dia, com pelo menos 15 min entre elas, deram exatamente os mesmos números. */
    public boolean estavel(LocalDate dia) {
        Foto a = fotoAnterior, b = fotoAtual;
        return a != null && b != null && a.dia().equals(dia) && b.dia().equals(dia)
                && !a.em().plusMinutes(15).isAfter(b.em())
                && a.movimento().equals(b.movimento());
    }

    private LocalDate ontem() {
        return LocalDate.now(clock.withZone(zona)).minusDays(1);
    }

    /** Coleta uma loja. Devolve true se deu certo. */
    public boolean coletarLoja(String merchantId) {
        LocalDate ontem = LocalDate.now(clock.withZone(zona)).minusDays(1);
        boolean historico = !repo.historicoOk(merchantId);
        LocalDate de = historico ? ontem.minusDays(cfg.diasHistorico() - 1L) : ontem.minusDays(cfg.diasReprocessar() - 1L);
        try {
            for (LocalDate ini = de; !ini.isAfter(ontem); ini = ini.plusDays(BLOCO_DIAS)) {
                LocalDate fim = ini.plusDays(BLOCO_DIAS - 1L).isAfter(ontem) ? ontem : ini.plusDays(BLOCO_DIAS - 1L);
                coletarBloco(merchantId, ini, fim);
            }
            if (!repo.temShortId(merchantId)) {
                String sid = LeitorKpis.lerShortId(fonte.kpis(merchantId, LeitorKpis.consultaShortId(ontem.minusDays(59), ontem)));
                if (sid != null) {
                    repo.salvarShortId(merchantId, sid, agora());
                }
            }
            OffsetDateTime curva = repo.curvaAtualizadaEm(merchantId);
            if (curva == null || curva.isBefore(agora().minusDays(7))) {
                atualizarCurva(merchantId, ontem);
            }
            repo.coletaOk(merchantId, agora(), true);
            return true;
        } catch (IfoodException e) {
            String msg = e.semPermissao() ? "Loja não autorizou o app de BI (" + e.getMessage() + ")" : e.getMessage();
            repo.coletaErro(merchantId, msg, agora());
            log.warn("[painel] {}: {}", merchantId, msg);
            return false;
        } catch (RuntimeException e) {
            repo.coletaErro(merchantId, e.toString(), agora());
            log.warn("[painel] {}: erro inesperado", merchantId, e);
            return false;
        }
    }

    void coletarBloco(String merchantId, LocalDate de, LocalDate ate) {
        JsonNode resp = fonte.kpis(merchantId, LeitorKpis.consultaDias(de, ate));
        Map<LocalDate, Dia> dias;
        Map<LocalDate, Integer> novos;
        if (de.equals(ate) || LeitorKpis.temDatas(resp) || resp.path("data").isEmpty()) {
            dias = LeitorKpis.lerDias(resp, de.equals(ate) ? de : null);
            novos = LeitorKpis.lerNovosClientes(fonte.kpis(merchantId, LeitorKpis.consultaNovosClientes(de, ate)),
                    de.equals(ate) ? de : null);
        } else {
            // a API não separou por dia: pergunta dia a dia
            dias = new LinkedHashMap<>();
            novos = new LinkedHashMap<>();
            for (LocalDate d = de; !d.isAfter(ate); d = d.plusDays(1)) {
                dias.putAll(LeitorKpis.lerDias(fonte.kpis(merchantId, LeitorKpis.consultaDias(d, d)), d));
                novos.putAll(LeitorKpis.lerNovosClientes(
                        fonte.kpis(merchantId, LeitorKpis.consultaNovosClientes(d, d)), d));
            }
        }
        // resposta toda zerada para um período que já tinha venda: o iFood ainda não liberou (ou falhou); não apaga
        boolean tudoZero = dias.values().stream().allMatch(d -> d.pedidos == 0 && d.cancelados == 0);
        if (tudoZero && repo.pedidosNoPeriodo(merchantId, de, ate) > 0) {
            log.info("[painel] {} {}..{}: resposta zerada, mantendo os números já salvos", merchantId, de, ate);
            return;
        }
        // dias sem pedido também ficam gravados (zerados), para distinguir "sem venda" de "sem coleta"
        for (LocalDate d = de; !d.isAfter(ate); d = d.plusDays(1)) {
            dias.computeIfAbsent(d, Dia::new);
        }
        repo.salvarDias(merchantId, dias.values(), novos, agora());
    }

    void atualizarCurva(String merchantId, LocalDate ate) {
        int semanas = Math.max(1, cfg.semanasCurva());
        LocalDate de = ate.minusDays(semanas * 7L - 1);
        List<LeitorKpis.Hora> horas = LeitorKpis.lerCurva(fonte.kpis(merchantId, LeitorKpis.consultaCurva(de, ate)));
        repo.salvarCurva(merchantId, horas, semanas, agora());
    }

    /** Resposta bruta do Analytics (para conferir o formato em produção). */
    public JsonNode diagnostico(String merchantId, LocalDate de, LocalDate ate) {
        return fonte.kpis(merchantId, LeitorKpis.consultaDias(de, ate));
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(clock);
    }
}
