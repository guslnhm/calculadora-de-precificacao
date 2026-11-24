package com.precificacao.precificacao.painel;

import com.precificacao.precificacao.ifood.IfoodProperties;
import com.precificacao.precificacao.painel.PainelRepository.Intervalo;
import com.precificacao.precificacao.painel.PainelRepository.LinhaDia;
import com.precificacao.precificacao.painel.PainelRepository.Manual;
import com.precificacao.precificacao.painel.PainelRepository.PeriodoBi;
import com.precificacao.precificacao.painel.Perdas.ChaveHora;
import com.precificacao.precificacao.painel.Perdas.Estimativa;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Números do painel por loja e período (semana seg–dom ou mês).
 *
 * Fonte de cada período: os dias do Analytics quando cobrem o período inteiro; senão o histórico do BI.
 * Conversão e repasse: lançamento manual, senão o BI. Perdas: incidentes do monitor × curva de venda por hora.
 */
@Service
public class PainelService {

    public static final String SEMANAL = "SEMANAL";
    public static final String MENSAL = "MENSAL";

    public record Periodo(String tipo, LocalDate inicio, LocalDate fim) {
        public long dias() {
            return ChronoUnit.DAYS.between(inicio, fim) + 1;
        }

        public Periodo anterior() {
            return SEMANAL.equals(tipo) ? semana(inicio.minusDays(7)) : mes(inicio.minusMonths(1));
        }
    }

    public static Periodo semana(LocalDate qualquerDia) {
        LocalDate ini = qualquerDia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new Periodo(SEMANAL, ini, ini.plusDays(6));
    }

    public static Periodo mes(LocalDate qualquerDia) {
        LocalDate ini = qualquerDia.withDayOfMonth(1);
        return new Periodo(MENSAL, ini, ini.with(TemporalAdjusters.lastDayOfMonth()));
    }

    /** Números de uma loja num período. Campos nulos = sem dado. */
    public record Metricas(String fonte, Integer pedidos, BigDecimal faturamento, BigDecimal ticket,
                           Integer cancelamentos, BigDecimal taxaCancelamento, Integer novosClientes,
                           BigDecimal conversao, BigDecimal repasse, BigDecimal tempoEntrega,
                           BigDecimal incentivoLoja, Long minutosFechada, BigDecimal perdaEstimada,
                           boolean conversaoManual, boolean repasseManual) {
    }

    public record Linha(String chave, String nome, String plataforma, Metricas atual, Metricas anterior,
                        Map<String, BigDecimal> variacao) {
    }

    public record Carteira(Periodo periodo, Periodo anterior, Map<String, Object> totais, List<Linha> linhas,
                           Map<String, Object> atualizacao) {
    }

    public record PontoSerie(Periodo periodo, Metricas metricas, boolean alteracaoCardapio) {
    }

    public record DiaLoja(LocalDate dia, int pedidos, int cancelados, BigDecimal faturamento,
                          Integer novosClientes, BigDecimal tempoEntrega) {
    }

    public record IncidenteLoja(OffsetDateTime inicio, OffsetDateTime fim, long minutos, BigDecimal perda) {
    }

    public record Loja(String chave, String nome, String plataforma, Periodo periodo, Metricas atual,
                       Metricas anterior, Map<String, BigDecimal> variacao, List<PontoSerie> serie,
                       List<DiaLoja> dias, List<IncidenteLoja> incidentes, List<Map<String, Object>> curva,
                       Map<String, Object> recordes) {
    }

    private final PainelRepository repo;
    private final Clock clock;
    private final ZoneId zona;

    @org.springframework.beans.factory.annotation.Autowired
    public PainelService(PainelRepository repo, Clock clock, IfoodProperties ifood) {
        this(repo, clock, ifood.zona());
    }

    public PainelService(PainelRepository repo, Clock clock, ZoneId zona) {
        this.repo = repo;
        this.clock = clock;
        this.zona = zona;
    }

    LocalDate hoje() {
        return LocalDate.now(clock.withZone(zona));
    }

    /** Período padrão: última semana fechada / último mês fechado. */
    public Periodo periodo(String tipo, LocalDate inicio) {
        String t = MENSAL.equalsIgnoreCase(tipo) ? MENSAL : SEMANAL;
        if (inicio != null) {
            return MENSAL.equals(t) ? mes(inicio) : semana(inicio);
        }
        LocalDate ontem = hoje().minusDays(1);
        return MENSAL.equals(t) ? mes(ontem.withDayOfMonth(1).minusDays(1)) : semana(ontem.minusDays(6));
    }

    // ----------------------------------------------------------------- carteira

    public Carteira carteira(String tipo, LocalDate inicio) {
        return carteira(tipo, inicio, null);
    }

    /**
     * Carteira; com {@code permitidas} (acesso de mentorado) mostra só essas lojas, inclusive as que ainda
     * não têm números no período.
     */
    public Carteira carteira(String tipo, LocalDate inicio, Set<String> permitidas) {
        Periodo p = periodo(tipo, inicio);
        Periodo ant = p.anterior();
        Map<Periodo, Map<String, Metricas>> calc = calcular(List.of(p, ant), null);
        Map<String, String[]> nomes = nomes();
        Set<String> chaves = new LinkedHashSet<>(calc.get(p).keySet());
        chaves.addAll(calc.get(ant).keySet());
        if (permitidas != null) {
            chaves.retainAll(permitidas);
            chaves.addAll(permitidas);
        }
        List<Linha> linhas = new ArrayList<>();
        for (String c : chaves) {
            Metricas a = calc.get(p).get(c);
            Metricas b = calc.get(ant).get(c);
            String[] n = nomes.getOrDefault(c, new String[]{c, "IFOOD"});
            linhas.add(new Linha(c, n[0], n[1], a, b, variacao(a, b)));
        }
        linhas.sort(Comparator.comparing((Linha l) -> l.atual() == null || l.atual().faturamento() == null
                ? BigDecimal.ZERO : l.atual().faturamento()).reversed());
        return new Carteira(p, ant, totais(linhas), linhas, atualizacao());
    }

    private Map<String, Object> totais(List<Linha> linhas) {
        BigDecimal fat = BigDecimal.ZERO, fatAnt = BigDecimal.ZERO, perda = BigDecimal.ZERO;
        int ped = 0, pedAnt = 0, canc = 0, lojas = 0;
        long minutos = 0;
        for (Linha l : linhas) {
            if (l.atual() != null && l.atual().faturamento() != null) {
                fat = fat.add(l.atual().faturamento());
                ped += nz(l.atual().pedidos());
                canc += nz(l.atual().cancelamentos());
                lojas++;
                if (l.atual().perdaEstimada() != null) {
                    perda = perda.add(l.atual().perdaEstimada());
                    minutos += l.atual().minutosFechada() == null ? 0 : l.atual().minutosFechada();
                }
            }
            if (l.anterior() != null && l.anterior().faturamento() != null) {
                fatAnt = fatAnt.add(l.anterior().faturamento());
                pedAnt += nz(l.anterior().pedidos());
            }
        }
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("lojas", lojas);
        t.put("faturamento", fat);
        t.put("faturamentoAnterior", fatAnt);
        t.put("pedidos", ped);
        t.put("pedidosAnterior", pedAnt);
        t.put("ticket", ped == 0 ? null : fat.divide(BigDecimal.valueOf(ped), 2, RoundingMode.HALF_UP));
        t.put("cancelamentos", canc);
        t.put("minutosFechada", minutos);
        t.put("perdaEstimada", perda);
        t.put("variacaoFaturamento", pct(fat, fatAnt));
        t.put("variacaoPedidos", pct(BigDecimal.valueOf(ped), BigDecimal.valueOf(pedAnt)));
        return t;
    }

    private Map<String, Object> atualizacao() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("biImportadoEm", repo.biImportadoEm());
        return m;
    }

    // ----------------------------------------------------------------- loja

    public Loja loja(String chave, String tipo, LocalDate inicio, int quantos) {
        Periodo p = periodo(tipo, inicio);
        List<Periodo> periodos = new ArrayList<>();
        Periodo x = p;
        for (int i = 0; i < Math.max(2, quantos); i++) {
            periodos.add(0, x);
            x = x.anterior();
        }
        Map<Periodo, Map<String, Metricas>> calc = calcular(periodos, chave);
        List<PontoSerie> serie = new ArrayList<>();
        java.util.Set<LocalDate> alteracoes = repo.alteracoesCardapio(chave, p.tipo(), periodos.get(0).inicio(), p.inicio());
        for (Periodo q : periodos) {
            serie.add(new PontoSerie(q, calc.get(q).get(chave), alteracoes.contains(q.inicio())));
        }
        Metricas a = calc.get(p).get(chave);
        Metricas b = calc.get(p.anterior()) == null ? null : calc.get(p.anterior()).get(chave);
        String[] n = nomes().getOrDefault(chave, new String[]{chave, "IFOOD"});

        List<DiaLoja> dias = new ArrayList<>();
        List<IncidenteLoja> incs = new ArrayList<>();
        List<Map<String, Object>> curva = new ArrayList<>();
        if (!chave.startsWith("bi:")) {
            for (LinhaDia d : repo.dias(chave, p.inicio(), p.fim())) {
                dias.add(new DiaLoja(d.dia(), d.pedidos(), d.cancelados(), d.gmv(), d.novosClientes(), d.tempoEntrega()));
            }
            Map<ChaveHora, BigDecimal> cv = repo.curvas().getOrDefault(chave, Map.of());
            OffsetDateTime agora = OffsetDateTime.now(clock);
            for (Intervalo i : unir(repo.incidentes(inicioDe(p), fimDe(p), agora))) {
                if (!i.merchantId().equals(chave)) {
                    continue;
                }
                Intervalo c = recortar(i, p);
                Estimativa e = Perdas.estimar(cv, c.inicio(), c.fim(), zona);
                incs.add(new IncidenteLoja(c.inicio(), c.fim(),
                        ChronoUnit.MINUTES.between(c.inicio(), c.fim()), e.valor()));
            }
            cv.forEach((k, v) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("diaSemana", k.diaSemana());
                m.put("hora", k.hora());
                m.put("faturamentoMedio", v);
                curva.add(m);
            });
        }
        return new Loja(chave, n[0], n[1], p, a, b, variacao(a, b), serie, dias, incs, curva, recordes(serie));
    }

    private Map<String, Object> recordes(List<PontoSerie> serie) {
        Map<String, Object> r = new LinkedHashMap<>();
        BigDecimal soma = BigDecimal.ZERO, max = null;
        int n = 0, somaPed = 0, maxPed = 0;
        for (PontoSerie s : serie) {
            if (s.metricas() == null || s.metricas().faturamento() == null) {
                continue;
            }
            n++;
            soma = soma.add(s.metricas().faturamento());
            max = max == null || s.metricas().faturamento().compareTo(max) > 0 ? s.metricas().faturamento() : max;
            somaPed += nz(s.metricas().pedidos());
            maxPed = Math.max(maxPed, nz(s.metricas().pedidos()));
        }
        r.put("periodos", n);
        r.put("mediaFaturamento", n == 0 ? null : soma.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP));
        r.put("recordeFaturamento", max);
        r.put("mediaPedidos", n == 0 ? null : somaPed / n);
        r.put("recordePedidos", n == 0 ? null : maxPed);
        return r;
    }

    // ----------------------------------------------------------------- cálculo

    /** Métricas por período e loja. chaveUnica != null limita a uma loja. */
    Map<Periodo, Map<String, Metricas>> calcular(List<Periodo> periodos, String chaveUnica) {
        LocalDate de = periodos.stream().map(Periodo::inicio).min(LocalDate::compareTo).orElseThrow();
        LocalDate ate = periodos.stream().map(Periodo::fim).max(LocalDate::compareTo).orElseThrow();
        String tipo = periodos.get(0).tipo();
        LocalDate ontem = hoje().minusDays(1);

        String merchant = chaveUnica == null || chaveUnica.startsWith("bi:") ? null : chaveUnica;
        List<LinhaDia> dias = chaveUnica != null && chaveUnica.startsWith("bi:") ? List.of() : repo.dias(merchant, de, ate);
        Map<String, List<LinhaDia>> diasPorLoja = new HashMap<>();
        for (LinhaDia d : dias) {
            diasPorLoja.computeIfAbsent(d.merchantId(), k -> new ArrayList<>()).add(d);
        }
        Map<String, Map<LocalDate, PeriodoBi>> bi = new HashMap<>();
        for (PeriodoBi b : repo.periodosBi(tipo, de, ate)) {
            if (chaveUnica == null || chaveUnica.equals(b.chave())) {
                bi.computeIfAbsent(b.chave(), k -> new HashMap<>()).merge(b.inicio(), b, PainelService::somarBi);
            }
        }
        Map<String, Map<LocalDate, Manual>> manuais = new HashMap<>();
        for (Manual m : repo.manuais(tipo, de, ate)) {
            manuais.computeIfAbsent(m.chave(), k -> new HashMap<>()).put(m.inicio(), m);
        }
        Map<String, Map<ChaveHora, BigDecimal>> curvas = repo.curvas();
        OffsetDateTime agora = OffsetDateTime.now(clock);
        List<Intervalo> incidentes = unir(repo.incidentes(inicioDe(new Periodo(tipo, de, ate)),
                fimDe(new Periodo(tipo, de, ate)), agora));

        Set<String> chaves = new LinkedHashSet<>();
        if (chaveUnica != null) {
            chaves.add(chaveUnica);
        } else {
            chaves.addAll(repo.lojasAtivas());
            chaves.addAll(diasPorLoja.keySet());
            chaves.addAll(bi.keySet());
        }

        Map<Periodo, Map<String, Metricas>> out = new LinkedHashMap<>();
        for (Periodo p : periodos) {
            Map<String, Metricas> porLoja = new LinkedHashMap<>();
            LocalDate fimEfetivo = p.fim().isAfter(ontem) ? ontem : p.fim();
            for (String c : chaves) {
                List<LinhaDia> ds = diasPorLoja.getOrDefault(c, List.of()).stream()
                        .filter(d -> !d.dia().isBefore(p.inicio()) && !d.dia().isAfter(p.fim())).toList();
                long esperado = fimEfetivo.isBefore(p.inicio()) ? 0
                        : ChronoUnit.DAYS.between(p.inicio(), fimEfetivo) + 1;
                PeriodoBi b = bi.getOrDefault(c, Map.of()).get(p.inicio());
                Manual m = manuais.getOrDefault(c, Map.of()).get(p.inicio());
                Estimativa perda = null;
                if (!c.startsWith("bi:")) {
                    perda = Estimativa.ZERO;
                    for (Intervalo i : incidentes) {
                        if (i.merchantId().equals(c)) {
                            Intervalo r = recortar(i, p);
                            if (r.fim().isAfter(r.inicio())) {
                                perda = perda.somar(Perdas.estimar(curvas.get(c), r.inicio(), r.fim(), zona));
                            }
                        }
                    }
                }
                Metricas met;
                if (esperado > 0 && ds.size() >= esperado) {
                    met = deDias(ds, b, m, perda);
                } else if (b != null) {
                    met = deBi(b, m, perda);
                } else if (!ds.isEmpty()) {
                    met = deDias(ds, null, m, perda);   // período em andamento ou coleta parcial
                } else if (m != null) {
                    met = new Metricas(null, null, null, null, null, null, null, m.conversao(), m.repasse(), null, null,
                            perda == null ? null : perda.minutos(), perda == null ? null : perda.valor(),
                            m.conversao() != null, m.repasse() != null);
                } else {
                    continue;
                }
                porLoja.put(c, met);
            }
            out.put(p, porLoja);
        }
        return out;
    }

    private static Metricas deDias(List<LinhaDia> ds, PeriodoBi b, Manual m, Estimativa perda) {
        int ped = 0, canc = 0, novos = 0;
        boolean temNovos = false;
        BigDecimal fat = BigDecimal.ZERO, inc = BigDecimal.ZERO, tempoSoma = BigDecimal.ZERO;
        int tempoPeso = 0;
        for (LinhaDia d : ds) {
            ped += d.pedidos();
            canc += d.cancelados();
            fat = fat.add(nz(d.gmv()));
            inc = inc.add(nz(d.incentivoLoja()));
            if (d.novosClientes() != null) {
                novos += d.novosClientes();
                temNovos = true;
            }
            if (d.tempoEntrega() != null && d.pedidos() > 0) {
                tempoSoma = tempoSoma.add(d.tempoEntrega().multiply(BigDecimal.valueOf(d.pedidos())));
                tempoPeso += d.pedidos();
            }
        }
        BigDecimal conv = m != null && m.conversao() != null ? m.conversao() : b == null ? null : b.conversao();
        BigDecimal rep = m != null && m.repasse() != null ? m.repasse() : b == null ? null : b.repasse();
        return new Metricas("ANALYTICS", ped, fat, ped == 0 ? null : fat.divide(BigDecimal.valueOf(ped), 2, RoundingMode.HALF_UP),
                canc, taxa(canc, ped + canc), temNovos ? novos : null, conv, rep,
                tempoPeso == 0 ? null : tempoSoma.divide(BigDecimal.valueOf(tempoPeso), 1, RoundingMode.HALF_UP),
                inc, perda == null ? null : perda.minutos(), perda == null ? null : perda.valor(),
                m != null && m.conversao() != null, m != null && m.repasse() != null);
    }

    private static Metricas deBi(PeriodoBi b, Manual m, Estimativa perda) {
        Integer ped = b.pedidos();
        Integer canc = b.cancelamentos();
        BigDecimal conv = m != null && m.conversao() != null ? m.conversao() : b.conversao();
        BigDecimal rep = m != null && m.repasse() != null ? m.repasse() : b.repasse();
        BigDecimal ticket = b.ticket() != null ? b.ticket()
                : ped == null || ped == 0 || b.faturamento() == null ? null
                : b.faturamento().divide(BigDecimal.valueOf(ped), 2, RoundingMode.HALF_UP);
        return new Metricas("BI", ped, b.faturamento(), ticket, canc,
                ped == null || canc == null ? null : taxa(canc, ped + canc), b.novosClientes(), conv, rep,
                b.tempoEntrega(), b.incentivosLoja(), perda == null ? null : perda.minutos(),
                perda == null ? null : perda.valor(), m != null && m.conversao() != null,
                m != null && m.repasse() != null);
    }

    /** Duas operações do BI ligadas à mesma loja no mesmo período: soma. */
    private static PeriodoBi somarBi(PeriodoBi a, PeriodoBi b) {
        Integer ped = soma(a.pedidos(), b.pedidos());
        BigDecimal fat = soma(a.faturamento(), b.faturamento());
        return new PeriodoBi(a.chave(), a.plataforma(), a.periodoTipo(), a.inicio(), a.fim(), ped, fat,
                ped == null || ped == 0 || fat == null ? null : fat.divide(BigDecimal.valueOf(ped), 2, RoundingMode.HALF_UP),
                soma(a.repasse(), b.repasse()), a.conversao() != null ? a.conversao() : b.conversao(),
                soma(a.novosClientes(), b.novosClientes()), a.tempoEntrega() != null ? a.tempoEntrega() : b.tempoEntrega(),
                soma(a.cancelamentos(), b.cancelamentos()), a.tempoAberto(), soma(a.incentivosIfood(), b.incentivosIfood()),
                soma(a.incentivosLoja(), b.incentivosLoja()));
    }

    /** Variação % (atual vs anterior) dos principais números. */
    static Map<String, BigDecimal> variacao(Metricas a, Metricas b) {
        Map<String, BigDecimal> v = new LinkedHashMap<>();
        if (a == null || b == null) {
            return v;
        }
        v.put("faturamento", pct(a.faturamento(), b.faturamento()));
        v.put("pedidos", pct(dec(a.pedidos()), dec(b.pedidos())));
        v.put("ticket", pct(a.ticket(), b.ticket()));
        v.put("cancelamentos", pct(dec(a.cancelamentos()), dec(b.cancelamentos())));
        v.put("novosClientes", pct(dec(a.novosClientes()), dec(b.novosClientes())));
        v.put("conversao", a.conversao() == null || b.conversao() == null ? null
                : a.conversao().subtract(b.conversao()).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
        v.put("repasse", pct(a.repasse(), b.repasse()));
        return v;
    }

    // ----------------------------------------------------------------- nomes

    /** chave -> {nome, plataforma}. */
    public record OpcaoLoja(String chave, String nome, String plataforma) {
    }

    /** Todas as lojas que podem aparecer no painel (para escolher nos acessos). */
    public List<OpcaoLoja> opcoesLojas() {
        List<OpcaoLoja> out = new ArrayList<>();
        nomes().forEach((k, v) -> out.add(new OpcaoLoja(k, v[0], v[1])));
        out.sort(Comparator.comparing((OpcaoLoja o) -> o.nome() == null ? "" : o.nome().toLowerCase()));
        return out;
    }

    Map<String, String[]> nomes() {
        Map<String, String[]> out = new HashMap<>();
        repo.nomesLojas().forEach((k, v) -> out.put(k, new String[]{v == null ? k : v, "IFOOD"}));
        for (PainelRepository.OperacaoBi op : repo.operacoesBi()) {
            String chave = op.merchantId() != null ? op.merchantId() : "bi:" + op.idOperacao();
            out.putIfAbsent(chave, new String[]{op.operacao() != null ? op.operacao() : op.loja(), op.plataforma()});
        }
        return out;
    }

    // ----------------------------------------------------------------- apoio

    private OffsetDateTime inicioDe(Periodo p) {
        return p.inicio().atStartOfDay(zona).toOffsetDateTime();
    }

    private OffsetDateTime fimDe(Periodo p) {
        return p.fim().plusDays(1).atStartOfDay(zona).toOffsetDateTime();
    }

    private Intervalo recortar(Intervalo i, Periodo p) {
        OffsetDateTime ini = i.inicio().isBefore(inicioDe(p)) ? inicioDe(p) : i.inicio();
        OffsetDateTime fim = i.fim().isAfter(fimDe(p)) ? fimDe(p) : i.fim();
        return new Intervalo(i.merchantId(), ini, fim.isBefore(ini) ? ini : fim);
    }

    /** Junta incidentes sobrepostos da mesma loja (várias operações/canais fechados ao mesmo tempo). */
    static List<Intervalo> unir(List<Intervalo> lista) {
        List<Intervalo> ord = new ArrayList<>(lista);
        ord.sort(Comparator.comparing(Intervalo::merchantId).thenComparing(Intervalo::inicio));
        List<Intervalo> out = new ArrayList<>();
        for (Intervalo i : ord) {
            Intervalo u = out.isEmpty() ? null : out.get(out.size() - 1);
            if (u != null && u.merchantId().equals(i.merchantId()) && !i.inicio().isAfter(u.fim())) {
                out.set(out.size() - 1, new Intervalo(u.merchantId(), u.inicio(),
                        i.fim().isAfter(u.fim()) ? i.fim() : u.fim()));
            } else {
                out.add(i);
            }
        }
        return out;
    }

    static BigDecimal pct(BigDecimal a, BigDecimal b) {
        if (a == null || b == null || b.signum() == 0) {
            return null;
        }
        return a.subtract(b).multiply(BigDecimal.valueOf(100)).divide(b, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal taxa(int canc, int total) {
        return total == 0 ? null : BigDecimal.valueOf(canc).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal dec(Integer i) {
        return i == null ? null : BigDecimal.valueOf(i);
    }

    private static int nz(Integer i) {
        return i == null ? 0 : i;
    }

    private static BigDecimal nz(BigDecimal b) {
        return b == null ? BigDecimal.ZERO : b;
    }

    private static Integer soma(Integer a, Integer b) {
        return a == null ? b : b == null ? a : a + b;
    }

    private static BigDecimal soma(BigDecimal a, BigDecimal b) {
        return a == null ? b : b == null ? a : a.add(b);
    }
}
