package com.precificacao.precificacao.painel.base;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Destaques do dia anterior para o grupo admin, a partir da base histórica (relatório de pedidos do Portal):
 * <ul>
 *   <li>custo do iFood estimado por loja (1 − taxa de repasse) acima do limite;</li>
 *   <li>recordes de faturamento, pedidos e ticket médio: histórico e desde a última alteração de cardápio
 *       (sem alteração marcada, os últimos 90 dias).</li>
 * </ul>
 *
 * Taxa de repasse do dia = (líquido + recebido via loja) ÷ bruto, só com os pedidos que já têm valor líquido no
 * relatório (os do fim da noite costumam vir sem, quando o relatório é exportado na manhã seguinte).
 * Recebido via loja = o que o cliente pagou com vale-refeição (exceto o do iFood) ou na entrega.
 * Bruto = valor dos itens, mais a taxa de entrega nas lojas de entrega própria.
 * Não entram os custos semanais (anúncios, mensalidade, antecipação): a taxa sai ~1 p.p. acima da do Portal.
 */
public final class Destaques {

    public static final BigDecimal LIMITE_CUSTO = new BigDecimal("0.35");
    /** Acima disso o farol fica vermelho; entre LIMITE_CUSTO e isso, amarelo. */
    public static final BigDecimal LIMITE_VERMELHO = new BigDecimal("0.38");
    static final int MIN_PEDIDOS_CUSTO = 5;
    static final BigDecimal MIN_COBERTURA = new BigDecimal("0.5");
    static final int MIN_DIAS_HISTORICO = 60;
    static final int MIN_DIAS_JANELA = 14;
    static final int DIAS_SEM_ALTERACAO = 90;
    static final int MIN_PEDIDOS_TICKET = 15;
    private static final Locale BR = new Locale("pt", "BR");
    private static final DateTimeFormatter DD_MM = DateTimeFormatter.ofPattern("dd/MM");

    private Destaques() {
    }

    /** Custo do iFood de uma loja no dia. */
    public record Custo(int idOperacao, String loja, int pedidos, int comValor, BigDecimal bruto, BigDecimal repasse) {

        public BigDecimal taxa() {
            return bruto == null || bruto.signum() <= 0 || repasse == null ? null
                    : repasse.divide(bruto, 4, RoundingMode.HALF_UP);
        }

        public BigDecimal custo() {
            BigDecimal t = taxa();
            return t == null ? null : BigDecimal.ONE.subtract(t);
        }

        public BigDecimal cobertura() {
            return pedidos == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(comValor).divide(BigDecimal.valueOf(pedidos), 4, RoundingMode.HALF_UP);
        }

        boolean alerta(BigDecimal limite) {
            BigDecimal c = custo();
            return c != null && pedidos >= MIN_PEDIDOS_CUSTO && cobertura().compareTo(MIN_COBERTURA) >= 0
                    && c.compareTo(limite) > 0;
        }
    }

    /** Um recorde: métrica = FATURAMENTO, PEDIDOS ou TICKET. */
    public record Recorde(int idOperacao, String loja, String metrica, BigDecimal valor, BigDecimal anterior,
                          LocalDate diaAnterior, boolean historico, LocalDate alteracao) {
    }

    /** Pedidos do dia na tabela pedidos (para saber se o relatório do dia já foi enviado). */
    public static int pedidosDoDia(Connection c, LocalDate dia) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT count(*) FROM public.pedidos WHERE data_hora_pedido >= ? AND data_hora_pedido < ?")) {
            ps.setTimestamp(1, Timestamp.valueOf(dia.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(dia.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public static List<Custo> custos(Connection c, LocalDate dia) throws SQLException {
        String ok = "p.status_final_pedido <> 'CANCELADO' AND p.valor_liquido IS NOT NULL";
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT a.id_operacao, max(l.nome_loja || ' - ' || o.nome_operacao) loja,
                       count(*) FILTER (WHERE p.status_final_pedido <> 'CANCELADO') pedidos,
                       count(*) FILTER (WHERE %1$s) com_valor,
                       sum(p.valor_itens + CASE WHEN o.id_tipo_entrega = 1 THEN coalesce(p.taxa_entrega_cliente, 0) ELSE 0 END)
                           FILTER (WHERE %1$s) bruto,
                       sum(p.valor_liquido + CASE WHEN (p.forma_pagamento ILIKE '%%Vale Refei%%'
                                                        AND p.forma_pagamento NOT ILIKE '%%IFOOD MEAL%%')
                                                       OR p.forma_pagamento ILIKE 'Pgto na Entrega%%'
                                                  THEN coalesce(p.total_pago_cliente, 0) ELSE 0 END)
                           FILTER (WHERE %1$s) repasse
                FROM public.pedidos p
                JOIN silver.dim_operacao_alias a ON a.id_loja_origem = p.id_loja AND a.id_plataforma = 1
                JOIN silver.dim_operacao o ON o.id_operacao = a.id_operacao
                JOIN silver.dim_loja l ON l.id_loja = o.id_loja
                WHERE p.data_hora_pedido >= ? AND p.data_hora_pedido < ?
                GROUP BY a.id_operacao""".formatted(ok))) {
            ps.setTimestamp(1, Timestamp.valueOf(dia.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(dia.plusDays(1).atStartOfDay()));
            List<Custo> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Custo(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getBigDecimal(5),
                            rs.getBigDecimal(6)));
                }
            }
            return out;
        }
    }

    public static List<Recorde> recordes(Connection c, LocalDate dia) throws SQLException {
        List<Recorde> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                WITH d AS (
                    SELECT f.id_operacao, f.data_hora_pedido::date dia, count(*) ped, sum(f.valor_itens) fat
                    FROM silver.fato_pedido f
                    WHERE f.id_plataforma = 1 AND f.status_final_pedido <> 'CANCELADO' AND f.data_hora_pedido < ?
                    GROUP BY 1, 2),
                alt AS (
                    SELECT id_operacao, max(data_inicio) ini FROM gold.financeiro_periodo
                    WHERE id_plataforma = 1 AND periodo_tipo = 'SEMANAL' AND alteracao_cardapio AND data_inicio <= ?
                    GROUP BY 1),
                hoje AS (SELECT * FROM d WHERE dia = ?)
                SELECT h.id_operacao, (SELECT l.nome_loja || ' - ' || o.nome_operacao FROM silver.dim_operacao o
                                         JOIN silver.dim_loja l ON l.id_loja = o.id_loja WHERE o.id_operacao = h.id_operacao) loja,
                       h.ped, h.fat, alt.ini, x.metrica, x.janela, x.dias, x.melhor, x.dia_melhor
                FROM hoje h
                LEFT JOIN alt ON alt.id_operacao = h.id_operacao
                CROSS JOIN LATERAL (
                    SELECT m.metrica, j.janela, count(*) dias,
                           max(CASE m.metrica WHEN 'FATURAMENTO' THEN d.fat WHEN 'PEDIDOS' THEN d.ped
                                              ELSE d.fat / d.ped END) melhor,
                           (array_agg(d.dia ORDER BY CASE m.metrica WHEN 'FATURAMENTO' THEN d.fat WHEN 'PEDIDOS' THEN d.ped
                                                                    ELSE d.fat / d.ped END DESC, d.dia DESC))[1] dia_melhor
                    FROM (VALUES ('FATURAMENTO'), ('PEDIDOS'), ('TICKET')) m(metrica)
                    CROSS JOIN (VALUES ('HISTORICO'), ('JANELA')) j(janela)
                    JOIN d ON d.id_operacao = h.id_operacao AND d.dia < h.dia
                          AND (m.metrica <> 'TICKET' OR d.ped >= ?)
                          AND (j.janela = 'HISTORICO' OR d.dia >= coalesce(alt.ini, h.dia - ?))
                    GROUP BY m.metrica, j.janela) x
                ORDER BY 1""")) {
            ps.setTimestamp(1, Timestamp.valueOf(dia.plusDays(1).atStartOfDay()));
            ps.setDate(2, java.sql.Date.valueOf(dia));
            ps.setDate(3, java.sql.Date.valueOf(dia));
            ps.setInt(4, MIN_PEDIDOS_TICKET);
            ps.setInt(5, DIAS_SEM_ALTERACAO);
            // por loja e métrica: primeiro o histórico; a janela só conta se não for também recorde histórico
            java.util.Map<String, Recorde> historicos = new java.util.HashMap<>();
            List<Object[]> janelas = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int op = rs.getInt(1);
                    String loja = rs.getString(2);
                    int ped = rs.getInt(3);
                    BigDecimal fat = rs.getBigDecimal(4);
                    java.sql.Date ini = rs.getDate(5);
                    String metrica = rs.getString(6);
                    String janela = rs.getString(7);
                    int dias = rs.getInt(8);
                    BigDecimal melhor = rs.getBigDecimal(9);
                    java.sql.Date diaMelhor = rs.getDate(10);
                    BigDecimal valor = switch (metrica) {
                        case "FATURAMENTO" -> fat;
                        case "PEDIDOS" -> BigDecimal.valueOf(ped);
                        default -> ped >= MIN_PEDIDOS_TICKET ? fat.divide(BigDecimal.valueOf(ped), 2, RoundingMode.HALF_UP) : null;
                    };
                    if (valor == null || melhor == null || valor.compareTo(melhor) <= 0) {
                        continue;
                    }
                    boolean historico = "HISTORICO".equals(janela);
                    if (dias < (historico ? MIN_DIAS_HISTORICO : MIN_DIAS_JANELA)) {
                        continue;
                    }
                    Recorde r = new Recorde(op, loja, metrica, valor, melhor, diaMelhor == null ? null : diaMelhor.toLocalDate(),
                            historico, ini == null ? null : ini.toLocalDate());
                    if (historico) {
                        historicos.put(op + "|" + metrica, r);
                    } else {
                        janelas.add(new Object[] {op + "|" + metrica, r});
                    }
                }
            }
            out.addAll(historicos.values());
            for (Object[] j : janelas) {
                if (!historicos.containsKey((String) j[0])) {
                    out.add((Recorde) j[1]);
                }
            }
        }
        out.sort(Comparator.comparing((Recorde r) -> !r.historico()).thenComparing(Recorde::loja)
                .thenComparing(r -> ordemMetrica(r.metrica())));
        return out;
    }

    private static int ordemMetrica(String m) {
        return switch (m) {
            case "FATURAMENTO" -> 0;
            case "PEDIDOS" -> 1;
            default -> 2;
        };
    }

    /**
     * <pre>
     * 📈 *Destaques de ontem — terça, 25/11*
     *
     * ⚠️ *Custo iFood acima de 35%* · 2 lojas
     * • Loja A: *41,2%* (52 pedidos)
     *
     * 🏆 *Recordes*
     * • *Loja B* · 🥇 recorde histórico de faturamento: R$ 6.120 (antes R$ 5.980, 12/10)
     * • *Loja C* · melhor faturamento desde a alteração de cardápio (09/11): R$ 980
     * </pre>
     * Devolve null quando não há nada a destacar.
     */
    public static String mensagem(LocalDate dia, List<Custo> custos, List<Recorde> recordes, BigDecimal limite) {
        List<Custo> altos = custos.stream().filter(x -> x.alerta(limite))
                .sorted(Comparator.comparing(Custo::custo).reversed()).toList();
        if (altos.isEmpty() && recordes.isEmpty()) {
            return null;
        }
        String nomeDia = dia.getDayOfWeek().getDisplayName(TextStyle.FULL, BR).replace("-feira", "") + ", " + dia.format(DD_MM);
        List<String> blocos = new ArrayList<>();
        blocos.add("📈 *Destaques de ontem — " + nomeDia + "*");
        if (!altos.isEmpty()) {
            long vermelhas = altos.stream().filter(Destaques::vermelho).count();
            List<String> l = new ArrayList<>();
            l.add("⚠️ *Custo iFood acima de " + pct(limite, 0) + "* · " + altos.size() + (altos.size() == 1 ? " loja" : " lojas")
                    + "\n🔴 acima de " + pct(LIMITE_VERMELHO, 0) + ": " + vermelhas
                    + " · 🟡 de " + pct(limite, 0) + " a " + pct(LIMITE_VERMELHO, 0) + ": " + (altos.size() - vermelhas));
            for (Custo x : altos) {
                String cob = x.cobertura().compareTo(new BigDecimal("0.95")) < 0
                        ? ", " + pct(x.cobertura(), 0) + " com valor fechado" : "";
                l.add((vermelho(x) ? "🔴 " : "🟡 ") + "*" + x.loja() + "*: *" + pct(x.custo(), 1) + "*\n"
                        + "_" + x.pedidos() + " pedidos" + cob + "_");
            }
            l.add("_Estimativa pelo relatório de pedidos (sem anúncios, mensalidade e antecipação)._");
            blocos.add(String.join("\n\n", l));
        }
        if (!recordes.isEmpty()) {
            List<String> l = new ArrayList<>();
            l.add("🏆 *Recordes*");
            for (Recorde r : recordes) {
                String o = switch (r.metrica()) {
                    case "FATURAMENTO" -> "faturamento";
                    case "PEDIDOS" -> "pedidos";
                    default -> "ticket médio";
                };
                String janela = r.historico() ? "🥇 recorde histórico de " + o
                        : r.alteracao() != null ? "melhor " + o + " desde a alteração de cardápio (" + r.alteracao().format(DD_MM) + ")"
                        : "melhor " + o + " dos últimos " + DIAS_SEM_ALTERACAO + " dias";
                String antes = r.anterior() == null ? "" : " (antes " + valor(r.metrica(), r.anterior())
                        + (r.diaAnterior() == null ? "" : ", " + r.diaAnterior().format(DD_MM)) + ")";
                l.add("*" + r.loja() + "*\n" + janela + ": " + valor(r.metrica(), r.valor()) + antes);
            }
            blocos.add(String.join("\n\n", l));
        }
        return String.join("\n\n", blocos);
    }

    static boolean vermelho(Custo x) {
        return x.custo().compareTo(LIMITE_VERMELHO) > 0;
    }

    private static String valor(String metrica, BigDecimal v) {
        if ("PEDIDOS".equals(metrica)) {
            return NumberFormat.getIntegerInstance(BR).format(v);
        }
        NumberFormat f = NumberFormat.getCurrencyInstance(BR);
        if ("FATURAMENTO".equals(metrica)) {
            f.setMaximumFractionDigits(0);
            f.setMinimumFractionDigits(0);
        }
        return f.format(v).replace('\u00a0', ' ').replace('\u202f', ' ');
    }

    private static String pct(BigDecimal v, int casas) {
        return String.format(BR, "%." + casas + "f%%", v.multiply(BigDecimal.valueOf(100)));
    }
}
