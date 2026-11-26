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
 * O iFood libera o dia anterior aos poucos depois das 9h (lojas inteiras zeradas, ou com parte dos pedidos).
 * Por isso o resumo só sai, a partir do horário MONITOR_RESUMO_DIARIO, quando:
 *  - duas leituras seguidas do dia (com 15+ min entre elas) deram os mesmos números, e
 *  - nenhuma loja que costuma vender nesse dia está zerada.
 * Se isso não acontecer até o meio-dia, sai mesmo assim. Depois do envio, se os números do iFood mudarem
 * (até 14h30), reenvia "(atualizado)" uma vez, só para os grupos das lojas que mudaram.
 *
 * Só entram na mensagem as lojas com algo relevante no dia: recorde de pedidos ou de faturamento (contra o
 * histórico do Painel, com pelo menos 28 dias de vendas), cancelamentos bem acima do normal da loja, ou loja
 * fechada 1h ou mais no horário de funcionamento. Grupo sem nenhuma loja assim não recebe nada.
 */
@Service
public class ResumoDiarioService {

    static final int MAX_LOJAS = 30;
    /** Dias com venda no histórico para valer recorde. */
    static final int MIN_DIAS_RECORDE = 28;
    /** Janela da média de cancelamentos. */
    static final int DIAS_MEDIA_CANCELADOS = 28;
    /** Cancelamentos: pelo menos N no dia e X vezes a média da loja. */
    static final int MIN_CANCELADOS = 5;
    static final double VEZES_MEDIA_CANCELADOS = 3.0;
    /** Loja fechada no horário de funcionamento por pelo menos isso. */
    static final long MIN_MINUTOS_FECHADA = 60;
    static final int DIAS_HISTORICO = 180;
    static final LocalTime LIMITE_ESPERA = LocalTime.of(12, 0);
    static final LocalTime LIMITE_CORRECAO = LocalTime.of(14, 30);
    private static final Locale BR = new Locale("pt", "BR");

    /** Números de uma loja no dia. pedidos == null: sem dados do Analytics. */
    public record Loja(String nome, Integer pedidos, BigDecimal faturamento, Integer cancelados,
                       int fechamentos, long minutosFechada, BigDecimal perda, List<String> destaques) {
        public Loja(String nome, Integer pedidos, BigDecimal faturamento, Integer cancelados,
                    int fechamentos, long minutosFechada, BigDecimal perda) {
            this(nome, pedidos, faturamento, cancelados, fechamentos, minutosFechada, perda, List.of());
        }

        public boolean relevante() {
            return !destaques.isEmpty();
        }
    }

    /** O que a loja já fez antes do dia: maiores marcas e média de cancelamentos. */
    public record Historico(int diasComVenda, int maxPedidos, LocalDate diaMaxPedidos, BigDecimal maxFaturamento,
                            LocalDate diaMaxFaturamento, double mediaCancelados) {
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
        LocalDate ontem = hoje.minusDays(1);
        return (!coleta.rodando() && dadosCompletos(ontem))
                || !agora.isBefore(hoje.atTime(LIMITE_ESPERA).atZone(zona).toOffsetDateTime());
    }

    /** O dia já veio inteiro do iFood: leituras estáveis e nenhuma loja zerada fora do normal. */
    public boolean dadosCompletos(LocalDate dia) {
        return repo.pedidosDoDia(dia) > 0 && coleta.estavel(dia) && repo.zeradasSuspeitas(dia).isEmpty();
    }

    /**
     * Depois do resumo de hoje, o iFood mudou os números de alguma loja (dia liberado aos poucos):
     * reenvia uma vez, marcado como "atualizado", para os grupos dessas lojas.
     */
    public boolean correcaoPendente() {
        return !lojasQueMudaram().isEmpty();
    }

    /** Ainda vale a pena reler o dia de ontem? (resumo não saiu, ou saiu e pode precisar de correção) */
    public boolean aguardandoOntem() {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        LocalDate hoje = agora.atZoneSameInstant(zona).toLocalDate();
        OffsetDateTime inicioHoje = hoje.atStartOfDay(zona).toOffsetDateTime();
        OffsetDateTime corrigido = monitorRepo.ultimoEnvio("resumo-corrigido");
        if (corrigido != null && !corrigido.isBefore(inicioHoje)) {
            return false;
        }
        LocalDate ontem = hoje.minusDays(1);
        OffsetDateTime enviado = monitorRepo.ultimoEnvio("resumo");
        boolean saiu = enviado != null && !enviado.isBefore(inicioHoje);
        // já saiu com o dia completo e nada mudou desde então: para de reler
        return !(saiu && dadosCompletos(ontem) && mudaram(repo.resumoEnviado(ontem), repo.movimentoPorLoja(ontem)).isEmpty());
    }

    Set<String> lojasQueMudaram() {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        LocalDate hoje = agora.atZoneSameInstant(zona).toLocalDate();
        OffsetDateTime inicioHoje = hoje.atStartOfDay(zona).toOffsetDateTime();
        if (!painelCfg.analyticsConfigurado() || coleta.rodando()
                || agora.isAfter(hoje.atTime(LIMITE_CORRECAO).atZone(zona).toOffsetDateTime())) {
            return Set.of();
        }
        OffsetDateTime enviado = monitorRepo.ultimoEnvio("resumo");
        OffsetDateTime corrigido = monitorRepo.ultimoEnvio("resumo-corrigido");
        if (enviado == null || enviado.isBefore(inicioHoje) || (corrigido != null && !corrigido.isBefore(inicioHoje))) {
            return Set.of();
        }
        LocalDate ontem = hoje.minusDays(1);
        // números parados; antes do meio-dia, também sem loja zerada fora do normal
        boolean prontos = agora.isBefore(hoje.atTime(LIMITE_ESPERA).atZone(zona).toOffsetDateTime())
                ? dadosCompletos(ontem)
                : repo.pedidosDoDia(ontem) > 0 && coleta.estavel(ontem);
        if (!prontos) {
            return Set.of();
        }
        Map<String, Integer> antes = repo.resumoEnviado(ontem);
        if (antes.isEmpty()) {
            return Set.of();
        }
        return mudaram(antes, repo.movimentoPorLoja(ontem));
    }

    /** Lojas cujo movimento (pedidos + cancelados) mudou entre o que foi enviado e o que o iFood tem agora. */
    static Set<String> mudaram(Map<String, Integer> enviado, Map<String, Integer> agora) {
        Set<String> out = new java.util.TreeSet<>();
        agora.forEach((m, n) -> {
            if (!java.util.Objects.equals(enviado.getOrDefault(m, 0), n)) {
                out.add(m);
            }
        });
        return out;
    }

    public synchronized Map<String, String> enviarCorrecao() {
        Set<String> mudaram = lojasQueMudaram();
        if (mudaram.isEmpty()) {
            return Map.of();
        }
        Map<String, String> r = enviar(null, true, mudaram);
        monitorRepo.registrarEnvio("resumo-corrigido", null, null, List.of(),
                "(resumo reenviado: o iFood completou " + mudaram.size() + " loja(s))", "SOMBRA", null, OffsetDateTime.now(clock));
        return r;
    }

    /** Envia a cada grupo o resumo do dia (padrão: ontem). */
    public synchronized Map<String, String> enviar(LocalDate dia) {
        return enviar(dia, false);
    }

    synchronized Map<String, String> enviar(LocalDate dia, boolean atualizado) {
        return enviar(dia, atualizado, null);
    }

    /** {@code soLojas}: só os grupos que têm alguma dessas lojas (correção); null = todos. */
    synchronized Map<String, String> enviar(LocalDate dia, boolean atualizado, Set<String> soLojas) {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        LocalDate ontem = agora.atZoneSameInstant(zona).toLocalDate().minusDays(1);
        LocalDate d = dia != null ? dia : ontem;
        Map<String, Loja> lojas = numeros(d, agora);
        if (d.equals(ontem)) {
            // guarda o que foi enviado, para reenviar "(atualizado)" se o iFood completar o dia depois
            repo.salvarResumoEnviado(d, repo.movimentoPorLoja(d), agora);
        }
        List<Grupo> todos = grupos.listar(true);
        Map<String, String> enviados = new LinkedHashMap<>();
        for (Grupo g : todos) {
            Set<String> ids = lojas.keySet().stream()
                    .filter(m -> GrupoWhatsappRepository.gruposDaLoja(todos, m).stream().anyMatch(x -> x.id() == g.id()))
                    .collect(Collectors.toSet());
            if (ids.isEmpty() || (soLojas != null && ids.stream().noneMatch(soLojas::contains))) {
                continue;
            }
            List<Loja> doGrupo = ids.stream().map(lojas::get).toList();
            String texto = mensagem(d, doGrupo);
            if (texto == null) {
                continue;   // nada relevante nas lojas do grupo
            }
            if (atualizado) {
                texto = texto.replaceFirst("\\*\n", " (atualizado)*\n");
                texto = texto.replaceFirst("^(📊 \\*Resumo iFood — [^*]+)\\*$", "$1 (atualizado)*");
            }
            envio.enviar("resumo", g, texto);
            enviados.put(g.nome(), texto);
        }
        if (enviados.isEmpty() && !atualizado) {
            // registra que o resumo de hoje já foi avaliado, para não tentar de novo a cada ciclo
            monitorRepo.registrarEnvio("resumo", null, null, List.of(), "(nada relevante no resumo de " + d + ")",
                    "SOMBRA", null, agora);
        }
        return enviados;
    }

    Map<String, Loja> numeros(LocalDate d, OffsetDateTime agora) {
        Map<String, String> nomes = repo.nomesLojas();
        Map<String, LinhaDia> dias = new HashMap<>();
        List<LinhaDia> doDia = repo.dias(null, d, d);
        // todas as lojas zeradas = o iFood ainda não liberou o dia: trata como "sem dados", não como zero pedidos
        if (doDia.stream().anyMatch(l -> l.pedidos() > 0 || l.cancelados() > 0)) {
            for (LinhaDia l : doDia) {
                dias.put(l.merchantId(), l);
            }
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
        Map<String, Historico> hist = historicos(repo.dias(null, d.minusDays(DIAS_HISTORICO), d.minusDays(1)), d);
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
            Loja loja = new Loja(nomes.getOrDefault(m, m), l == null ? null : l.pedidos(), l == null ? null : l.gmv(),
                    l == null ? null : l.cancelados(), lista.size(), min, perda);
            out.put(m, comDestaques(loja, hist.get(m)));
        }
        return out;
    }

    static Map<String, Historico> historicos(List<LinhaDia> linhas, LocalDate d) {
        Map<String, List<LinhaDia>> porLoja = new HashMap<>();
        for (LinhaDia l : linhas) {
            if (l.dia().isBefore(d)) {
                porLoja.computeIfAbsent(l.merchantId(), k -> new ArrayList<>()).add(l);
            }
        }
        Map<String, Historico> out = new HashMap<>();
        LocalDate inicioMedia = d.minusDays(DIAS_MEDIA_CANCELADOS);
        porLoja.forEach((m, dias) -> {
            int comVenda = 0;
            int maxPed = 0;
            LocalDate diaPed = null;
            BigDecimal maxFat = BigDecimal.ZERO;
            LocalDate diaFat = null;
            int canc = 0;
            int diasMedia = 0;
            for (LinhaDia l : dias) {
                if (l.pedidos() <= 0) {
                    continue;
                }
                comVenda++;
                if (l.pedidos() > maxPed) {
                    maxPed = l.pedidos();
                    diaPed = l.dia();
                }
                if (l.gmv() != null && l.gmv().compareTo(maxFat) > 0) {
                    maxFat = l.gmv();
                    diaFat = l.dia();
                }
                if (!l.dia().isBefore(inicioMedia)) {
                    canc += l.cancelados();
                    diasMedia++;
                }
            }
            out.put(m, new Historico(comVenda, maxPed, diaPed, maxFat, diaFat, diasMedia == 0 ? 0 : (double) canc / diasMedia));
        });
        return out;
    }

    /** Marca o que foi relevante no dia (recordes, cancelamentos fora do normal, loja fechada muito tempo). */
    static Loja comDestaques(Loja l, Historico h) {
        List<String> d = new ArrayList<>();
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM");
        if (l.pedidos() != null && h != null && h.diasComVenda() >= MIN_DIAS_RECORDE) {
            if (l.faturamento() != null && h.diaMaxFaturamento() != null && l.faturamento().compareTo(h.maxFaturamento()) > 0) {
                d.add("🏆 *Recorde de faturamento!* (antes " + reais(h.maxFaturamento()) + ", em " + h.diaMaxFaturamento().format(f) + ")");
            }
            if (h.diaMaxPedidos() != null && l.pedidos() > h.maxPedidos()) {
                d.add("🏆 *Recorde de pedidos!* (antes " + inteiro(h.maxPedidos()) + ", em " + h.diaMaxPedidos().format(f) + ")");
            }
        }
        int c = l.cancelados() == null ? 0 : l.cancelados();
        double media = h == null ? 0 : h.mediaCancelados();
        if (l.pedidos() != null && c >= MIN_CANCELADOS && c >= VEZES_MEDIA_CANCELADOS * media) {
            String normal = media < 0.5 ? "quase nunca tem cancelamento"
                    : "costuma ter " + String.format(BR, media < 10 ? "%.1f" : "%.0f", media).replace(",0", "") + " por dia";
            d.add("🚨 *Cancelamentos acima do normal:* " + c + " (a loja " + normal + ")");
        }
        if (l.minutosFechada() >= MIN_MINUTOS_FECHADA) {
            d.add("⏰ *Loja ficou fechada " + com.precificacao.precificacao.monitor.Mensagens.duracao(Duration.ofMinutes(l.minutosFechada()))
                    + "* no horário de funcionamento");
        }
        return new Loja(l.nome(), l.pedidos(), l.faturamento(), l.cancelados(), l.fechamentos(), l.minutosFechada(), l.perda(), d);
    }

    /**
     * Só as lojas com destaque; null se nenhuma.
     * <pre>
     * 📊 *Resumo iFood — segunda, 17/11*
     *
     * *Vo no Box - Asa Norte*
     * 🏆 *Recorde de faturamento!* (antes R$ 9.400, em 02/11)
     * ⏰ *Loja ficou fechada 1h20* no horário de funcionamento
     * 🧾 152 pedidos · R$ 9.870 · ticket R$ 64,93
     * ❌ 3 cancelados (1,9%)
     * ⏸️ 2 fechamentos · 1h20 · ≈ R$ 640 em vendas
     *
     * _As outras 2 lojas do grupo tiveram um dia sem nada fora do normal._
     * </pre>
     */
    static String mensagem(LocalDate d, List<Loja> todas) {
        List<Loja> lojas = todas.stream().filter(Loja::relevante).toList();
        if (lojas.isEmpty()) {
            return null;
        }
        String dia = d.getDayOfWeek().getDisplayName(TextStyle.FULL, BR).replace("-feira", "") + ", "
                + d.format(DateTimeFormatter.ofPattern("dd/MM"));
        List<String> blocos = new ArrayList<>();
        StringBuilder topo = new StringBuilder("📊 *Resumo iFood — ").append(dia).append("*");
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
        int normais = todas.size() - lojas.size();
        if (normais > 0) {
            blocos.add("_" + (normais == 1 ? "A outra loja do grupo teve um dia sem nada fora do normal."
                    : "As outras " + normais + " lojas do grupo tiveram um dia sem nada fora do normal.") + "_");
        }
        return String.join("\n\n", blocos);
    }

    static String bloco(Loja l) {
        List<String> linhas = new ArrayList<>();
        linhas.add("*" + l.nome() + "*");
        linhas.addAll(l.destaques());
        if (l.pedidos() != null) {   // sem dados de vendas (loja sem acesso ao app de BI): mostra só os fechamentos
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
