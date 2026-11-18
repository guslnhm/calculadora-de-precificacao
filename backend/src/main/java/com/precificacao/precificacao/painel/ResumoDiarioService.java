package com.precificacao.precificacao.painel;

import com.precificacao.precificacao.ifood.IfoodProperties;
import com.precificacao.precificacao.monitor.EnvioWhatsapp;
import com.precificacao.precificacao.monitor.GrupoWhatsappRepository;
import com.precificacao.precificacao.monitor.MonitorModelos.Grupo;
import com.precificacao.precificacao.monitor.MonitorProperties;
import com.precificacao.precificacao.monitor.MonitorRepository;
import com.precificacao.precificacao.painel.PainelRepository.Intervalo;
import com.precificacao.precificacao.painel.PainelRepository.LinhaDia;
import com.precificacao.precificacao.painel.Perdas.ChaveHora;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resumo iFood do dia anterior, um por grupo: pedidos, faturamento, cancelamentos (Analytics) e
 * fechamentos com a venda estimada perdida (monitor × curva por hora).
 *
 * Sai depois que a coleta do Analytics do dia terminou (9h40 em diante), nunca antes do horário
 * MONITOR_RESUMO_DIARIO; se a coleta não rodar, sai mesmo assim ao meio-dia, só com os fechamentos.
 */
@Service
public class ResumoDiarioService {

    static final int MAX_LOJAS = 30;
    static final LocalTime LIMITE_ESPERA = LocalTime.of(12, 0);
    private static final Locale BR = new Locale("pt", "BR");

    /** Números de uma loja no dia. pedidos == null: sem dados do Analytics. */
    public record Loja(String nome, Integer pedidos, BigDecimal faturamento, Integer cancelados,
                       int fechamentos, long minutosFechada, BigDecimal perda) {
    }

    private final PainelRepository repo;
    private final PainelColetaService coleta;
    private final PainelProperties painelCfg;
    private final MonitorProperties monitorCfg;
    private final MonitorRepository monitorRepo;
    private final GrupoWhatsappRepository grupos;
    private final EnvioWhatsapp envio;
    private final Clock clock;
    private final ZoneId zona;

    public ResumoDiarioService(PainelRepository repo, PainelColetaService coleta, PainelProperties painelCfg,
                               MonitorProperties monitorCfg, MonitorRepository monitorRepo,
                               GrupoWhatsappRepository grupos, EnvioWhatsapp envio, Clock clock, IfoodProperties ifood) {
        this.repo = repo;
        this.coleta = coleta;
        this.painelCfg = painelCfg;
        this.monitorCfg = monitorCfg;
        this.monitorRepo = monitorRepo;
        this.grupos = grupos;
        this.envio = envio;
        this.clock = clock;
        this.zona = ifood.zona();
    }

    public boolean pendente() {
        String h = monitorCfg.resumoDiario();
        if (h == null || h.isBlank()) {
            return false;
        }
        OffsetDateTime agora = OffsetDateTime.now(clock);
        LocalDate hoje = agora.atZoneSameInstant(zona).toLocalDate();
        OffsetDateTime alvo = hoje.atTime(LocalTime.parse(h)).atZone(zona).toOffsetDateTime();
        if (agora.isBefore(alvo)) {
            return false;
        }
        OffsetDateTime ultimo = monitorRepo.ultimoEnvio("resumo");
        if (ultimo != null && !ultimo.isBefore(alvo)) {
            return false;
        }
        if (!painelCfg.analyticsConfigurado() || !painelCfg.coletaAtiva()) {
            return true;
        }
        OffsetDateTime terminou = coleta.terminouEm();
        boolean coletouHoje = !coleta.rodando() && terminou != null
                && !terminou.isBefore(hoje.atStartOfDay(zona).toOffsetDateTime());
        return coletouHoje || !agora.isBefore(hoje.atTime(LIMITE_ESPERA).atZone(zona).toOffsetDateTime());
    }

    /** Envia a cada grupo o resumo do dia (padrão: ontem). */
    public synchronized Map<String, String> enviar(LocalDate dia) {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        LocalDate d = dia != null ? dia : agora.atZoneSameInstant(zona).toLocalDate().minusDays(1);
        Map<String, Loja> lojas = numeros(d, agora);
        List<Grupo> todos = grupos.listar(true);
        Map<String, String> enviados = new LinkedHashMap<>();
        for (Grupo g : todos) {
            Set<String> ids = lojas.keySet().stream()
                    .filter(m -> GrupoWhatsappRepository.gruposDaLoja(todos, m).stream().anyMatch(x -> x.id() == g.id()))
                    .collect(Collectors.toSet());
            if (ids.isEmpty()) {
                continue;
            }
            List<Loja> doGrupo = ids.stream().map(lojas::get).toList();
            String texto = mensagem(d, doGrupo);
            envio.enviar("resumo", g, texto);
            enviados.put(g.nome(), texto);
        }
        return enviados;
    }

    Map<String, Loja> numeros(LocalDate d, OffsetDateTime agora) {
        Map<String, String> nomes = repo.nomesLojas();
        Map<String, LinhaDia> dias = new HashMap<>();
        for (LinhaDia l : repo.dias(null, d, d)) {
            dias.put(l.merchantId(), l);
        }
        OffsetDateTime de = d.atStartOfDay(zona).toOffsetDateTime();
        OffsetDateTime ate = d.plusDays(1).atStartOfDay(zona).toOffsetDateTime();
        Map<String, List<Intervalo>> incs = new HashMap<>();
        for (Intervalo i : PainelService.unir(repo.incidentes(de, ate, agora))) {
            OffsetDateTime ini = i.inicio().isBefore(de) ? de : i.inicio();
            OffsetDateTime fim = i.fim().isAfter(ate) ? ate : i.fim();
            if (fim.isAfter(ini)) {
                incs.computeIfAbsent(i.merchantId(), k -> new ArrayList<>()).add(new Intervalo(i.merchantId(), ini, fim));
            }
        }
        Map<String, Map<ChaveHora, BigDecimal>> curvas = repo.curvas();
        Map<String, Loja> out = new LinkedHashMap<>();
        for (String m : repo.lojasAtivas()) {
            LinhaDia l = dias.get(m);
            long min = 0;
            BigDecimal perda = BigDecimal.ZERO;
            List<Intervalo> lista = incs.getOrDefault(m, List.of());
            for (Intervalo i : lista) {
                min += Duration.between(i.inicio(), i.fim()).toMinutes();
                perda = perda.add(Perdas.estimar(curvas.get(m), i.inicio(), i.fim(), zona).valor());
            }
            out.put(m, new Loja(nomes.getOrDefault(m, m), l == null ? null : l.pedidos(), l == null ? null : l.gmv(),
                    l == null ? null : l.cancelados(), lista.size(), min, perda));
        }
        return out;
    }

    /**
     * <pre>
     * 📊 *Resumo iFood — segunda, 17/11*
     * 3 lojas · 412 pedidos · R$ 26.310 · 5 cancelados
     *
     * *Vo no Box - Asa Norte*
     * 🧾 152 pedidos · R$ 9.870 · ticket R$ 64,93
     * ❌ 3 cancelados (1,9%)
     * ⏸️ 2 fechamentos · 1h20 · ≈ R$ 640 em vendas
     * </pre>
     */
    static String mensagem(LocalDate d, List<Loja> lojas) {
        String dia = d.getDayOfWeek().getDisplayName(TextStyle.FULL, BR).replace("-feira", "") + ", "
                + d.format(DateTimeFormatter.ofPattern("dd/MM"));
        List<String> blocos = new ArrayList<>();
        StringBuilder topo = new StringBuilder("📊 *Resumo iFood — ").append(dia).append("*");
        if (lojas.size() > 1) {
            int ped = lojas.stream().mapToInt(l -> l.pedidos() == null ? 0 : l.pedidos()).sum();
            int canc = lojas.stream().mapToInt(l -> l.cancelados() == null ? 0 : l.cancelados()).sum();
            BigDecimal fat = lojas.stream().map(l -> l.faturamento() == null ? BigDecimal.ZERO : l.faturamento())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long comFechamento = lojas.stream().filter(l -> l.fechamentos() > 0).count();
            topo.append("\n").append(lojas.size()).append(" lojas · ").append(inteiro(ped)).append(" pedidos · ")
                    .append(reais(fat)).append(" · ").append(canc).append(canc == 1 ? " cancelado" : " cancelados");
            if (comFechamento > 0) {
                topo.append("\n⏸️ ").append(comFechamento).append(comFechamento == 1 ? " loja teve" : " lojas tiveram")
                        .append(" fechamento");
            }
        }
        blocos.add(topo.toString());
        List<Loja> ordem = new ArrayList<>(lojas);
        ordem.sort(Comparator.comparing((Loja l) -> l.faturamento() == null ? BigDecimal.ZERO : l.faturamento()).reversed()
                .thenComparing(Loja::nome));
        for (Loja l : ordem.subList(0, Math.min(MAX_LOJAS, ordem.size()))) {
            blocos.add(bloco(l));
        }
        if (ordem.size() > MAX_LOJAS) {
            blocos.add("… e mais " + (ordem.size() - MAX_LOJAS) + " loja(s) no Painel da calculadora.");
        }
        return String.join("\n\n", blocos);
    }

    static String bloco(Loja l) {
        List<String> linhas = new ArrayList<>();
        linhas.add("*" + l.nome() + "*");
        if (l.pedidos() == null) {
            linhas.add("🧾 sem dados de vendas do iFood");
        } else {
            String ticket = l.pedidos() > 0 && l.faturamento() != null
                    ? " · ticket " + reaisCentavos(l.faturamento().divide(BigDecimal.valueOf(l.pedidos()), 2, RoundingMode.HALF_UP))
                    : "";
            linhas.add("🧾 " + inteiro(l.pedidos()) + (l.pedidos() == 1 ? " pedido" : " pedidos") + " · "
                    + reais(l.faturamento()) + ticket);
            int c = l.cancelados() == null ? 0 : l.cancelados();
            if (c > 0) {
                int total = l.pedidos() + c;
                String pct = String.format(BR, "%.1f%%", 100.0 * c / total);
                linhas.add("❌ " + c + (c == 1 ? " cancelado" : " cancelados") + " (" + pct + ")");
            }
        }
        if (l.fechamentos() > 0) {
            String perda = l.perda() != null && l.perda().signum() > 0 ? " · ≈ " + reais(l.perda()) + " em vendas" : "";
            linhas.add("⏸️ " + l.fechamentos() + (l.fechamentos() == 1 ? " fechamento" : " fechamentos") + " · "
                    + com.precificacao.precificacao.monitor.Mensagens.duracao(Duration.ofMinutes(l.minutosFechada())) + perda);
        } else {
            linhas.add("✅ sem fechamentos");
        }
        return String.join("\n", linhas);
    }

    private static String inteiro(int n) {
        return NumberFormat.getIntegerInstance(BR).format(n);
    }

    private static String reais(BigDecimal v) {
        NumberFormat f = NumberFormat.getCurrencyInstance(BR);
        f.setMaximumFractionDigits(0);
        f.setMinimumFractionDigits(0);
        return f.format(v == null ? BigDecimal.ZERO : v).replace(' ', ' ');
    }

    private static String reaisCentavos(BigDecimal v) {
        return NumberFormat.getCurrencyInstance(BR).format(v).replace(' ', ' ');
    }
}
