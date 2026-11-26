package com.precificacao.precificacao.food99;

import com.precificacao.precificacao.ifood.IfoodProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coleta do financeiro da 99Food: lojas vinculadas ao app e lançamentos por pedido (Get Bill Data).
 * A 99 libera os dados com até 2 dias de atraso, então cada coleta relê os últimos N dias.
 */
@Service
public class Food99Service {

    private static final Logger log = LoggerFactory.getLogger(Food99Service.class);
    /** Período de cada consulta ao extrato (a 99 não documenta limite; blocos pequenos são mais seguros). */
    static final int DIAS_POR_CONSULTA = 7;
    static final int MAX_DIAS = 120;

    public record Resultado(int lojas, int lancamentos, List<String> erros) {
    }

    /** Um dia de uma loja em reais. */
    public record DiaReais(String shopId, String loja, LocalDate dia, int pedidos, int cancelados, BigDecimal valorItens,
                           BigDecimal descontoLoja, BigDecimal subsidio99, BigDecimal entregaLoja, BigDecimal comissao,
                           BigDecimal taxaPagamento, BigDecimal taxaEntrega, BigDecimal outrosLancamentos,
                           BigDecimal liquido, BigDecimal valeRefeicao, BigDecimal dinheiro) {
    }

    private final Food99Client client;
    private final Food99Repository repo;
    private final Food99Properties props;
    private final Clock clock;
    private final ZoneId zona;
    private final AtomicBoolean rodando = new AtomicBoolean(false);

    public Food99Service(Food99Client client, Food99Repository repo, Food99Properties props, Clock clock,
                         IfoodProperties ifood) {
        this.client = client;
        this.repo = repo;
        this.props = props;
        this.clock = clock;
        this.zona = ifood.zona();
    }

    public boolean configurado() {
        return props.configurado();
    }

    public boolean rodando() {
        return rodando.get();
    }

    public String linkAutorizacao() {
        return client.linkAutorizacao();
    }

    /** Relê os últimos N dias (até ontem) de todas as lojas vinculadas. */
    public Resultado coletarRecentes() {
        LocalDate ontem = LocalDate.now(clock.withZone(zona)).minusDays(1);
        return coletar(ontem.minusDays(Math.max(1, props.diasReler()) - 1L), ontem);
    }

    public Resultado coletar(LocalDate de, LocalDate ate) {
        if (de.isAfter(ate) || de.plusDays(MAX_DIAS).isBefore(ate)) {
            throw new IllegalArgumentException("Período inválido (máximo " + MAX_DIAS + " dias)");
        }
        if (!rodando.compareAndSet(false, true)) {
            return new Resultado(0, 0, List.of("Já tem uma coleta da 99Food rodando"));
        }
        try {
            OffsetDateTime agora = OffsetDateTime.now(clock);
            List<Food99Client.Loja> lojas = client.lojas();
            repo.salvarLojas(lojas, agora);
            int n = 0;
            int lidas = 0;
            List<String> erros = new ArrayList<>();
            for (Food99Client.Loja l : lojas) {
                if (!l.vinculada()) {
                    continue;
                }
                lidas++;
                String codigo = l.appShopId() != null ? l.appShopId() : l.shopId();
                try {
                    for (LocalDate ini = de; !ini.isAfter(ate); ini = ini.plusDays(DIAS_POR_CONSULTA)) {
                        LocalDate fim = ini.plusDays(DIAS_POR_CONSULTA - 1L).isAfter(ate) ? ate : ini.plusDays(DIAS_POR_CONSULTA - 1L);
                        int pagina = 1;
                        Food99Client.Pagina p;
                        do {
                            p = client.extrato(codigo, ini, fim, pagina);
                            n += repo.salvarLancamentos(l.shopId(), p.itens(), zona, agora);
                            pagina++;
                        } while (pagina <= p.totalPaginas());
                    }
                    repo.coletaOk(l.shopId(), agora);
                } catch (RuntimeException e) {
                    log.warn("99Food: extrato da loja {} ({}) falhou: {}", l.nome(), l.shopId(), e.getMessage());
                    repo.coletaErro(l.shopId(), e.getMessage());
                    erros.add((l.nome() != null ? l.nome() : l.shopId()) + ": " + e.getMessage());
                }
            }
            return new Resultado(lidas, n, erros);
        } finally {
            rodando.set(false);
        }
    }

    public Map<String, Object> situacao() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configurado", configurado());
        m.put("coletaAtiva", props.coletaAtiva());
        m.put("rodando", rodando());
        m.put("lojas", repo.lojas());
        return m;
    }

    public List<DiaReais> dias(LocalDate de, LocalDate ate) {
        BigDecimal div = BigDecimal.valueOf(Math.max(1, props.divisorValores()));
        return repo.dias(de, ate).stream().map(d -> new DiaReais(d.shopId(), d.loja(), d.dia(), d.pedidos(), d.cancelados(),
                r(d.valorItens(), div), r(d.descontoLoja(), div), r(d.subsidio99(), div), r(d.entregaLoja(), div),
                r(d.comissao(), div), r(d.taxaPagamento(), div), r(d.taxaEntrega(), div), r(d.outrosLancamentos(), div),
                r(d.liquido(), div), r(d.valeRefeicao(), div), r(d.dinheiro(), div))).toList();
    }

    private static BigDecimal r(long v, BigDecimal div) {
        return BigDecimal.valueOf(v).divide(div, 2, RoundingMode.HALF_UP);
    }
}
