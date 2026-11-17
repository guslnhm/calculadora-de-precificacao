package com.precificacao.precificacao.painel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Monta as consultas ao Analytics (POST /merchants/{id}/orders/kpis) e traduz as respostas.
 *
 * O Analytics só devolve números agregados. Para ter um número por dia usamos dateIntervals (um por dia)
 * junto com groupBy; cada item da resposta traz dateRangeKey e/ou groupByKey e as métricas pedidas.
 */
public final class LeitorKpis {

    /** Quem faz a chamada HTTP (trocado nos testes). */
    public interface Fonte {
        JsonNode kpis(String merchantId, JsonNode corpo);
    }

    /** Métricas de um dia de uma loja. */
    public static final class Dia {
        public final LocalDate dia;
        public int pedidos;
        public int cancelados;
        public BigDecimal gmv = BigDecimal.ZERO;
        public BigDecimal gmvCancelado = BigDecimal.ZERO;
        public Integer novosClientes;
        public BigDecimal incentivoLoja;
        public BigDecimal tempoEntrega;
        public BigDecimal tempoPreparo;
        public BigDecimal atraso;

        public Dia(LocalDate dia) {
            this.dia = dia;
        }
    }

    /** Venda somada de um dia da semana (1=seg..7=dom) e hora no período da curva. */
    public record Hora(int diaSemana, int hora, BigDecimal gmv, int pedidos) {
    }

    private static final JsonNodeFactory F = JsonNodeFactory.instance;
    private static final String[] METRICAS_MEDIA = {"timeToDeliveryInMin", "preparationTimeInMin", "timeDelayedInMin"};

    private LeitorKpis() {
    }

    // ----------------------------------------------------------------- consultas

    /** Pedidos, cancelamentos, GMV, incentivo da loja e tempos, por dia e status. */
    public static ObjectNode consultaDias(LocalDate de, LocalDate ate) {
        ObjectNode agg = F.objectNode();
        agg.set("dateIntervals", intervalos(de, ate));
        agg.putObject("groupBy").putArray("fields").add("orderStatus");
        ObjectNode metrics = agg.putObject("metrics");
        metrics.putArray("gmv").add("sum");
        metrics.putArray("benefitByTypePartnerTotalValue").add("sum");
        for (String m : METRICAS_MEDIA) {
            metrics.putArray(m).add("avg");
        }
        return corpo(de, ate, agg, null);
    }

    /** Pedidos concluídos que foram o 1º do cliente na loja, por dia. */
    public static ObjectNode consultaNovosClientes(LocalDate de, LocalDate ate) {
        ObjectNode agg = F.objectNode();
        agg.set("dateIntervals", intervalos(de, ate));
        agg.putObject("groupBy").putArray("fields").add("customerFirstOrderInMerchant");
        return corpo(de, ate, agg, "CONCLUDED");
    }

    /** GMV e pedidos concluídos por dia da semana e hora (curva para estimar perdas). */
    public static ObjectNode consultaCurva(LocalDate de, LocalDate ate) {
        ObjectNode agg = F.objectNode();
        ArrayNode campos = agg.putObject("groupBy").putArray("fields");
        campos.add("dayOfWeek").add("orderCreationHour");
        agg.putObject("metrics").putArray("gmv").add("sum");
        ObjectNode c = corpo(de, ate, agg, "CONCLUDED");
        c.put("size", 500);
        return c;
    }

    private static ObjectNode corpo(LocalDate de, LocalDate ate, ObjectNode agg, String status) {
        ObjectNode c = F.objectNode();
        c.put("page", 1);
        c.put("size", 1000);
        ObjectNode filtro = c.putObject("filter");
        ObjectNode ref = filtro.putObject("referenceDate");
        ref.put("gte", de + " 00:00:00");
        ref.put("lte", ate + " 23:59:59");
        if (status != null) {
            filtro.putArray("orderStatus").add(status);
        }
        c.set("agg", agg);
        return c;
    }

    private static ArrayNode intervalos(LocalDate de, LocalDate ate) {
        ArrayNode a = F.arrayNode();
        for (LocalDate d = de; !d.isAfter(ate); d = d.plusDays(1)) {
            a.addObject().put("from", d.toString()).put("to", d.plusDays(1).toString());
        }
        return a;
    }

    // ----------------------------------------------------------------- respostas

    /**
     * Junta a resposta de consultaDias. Sem dateRangeKey no item (resposta de um dia só), usa diaUnico.
     */
    public static Map<LocalDate, Dia> lerDias(JsonNode resp, LocalDate diaUnico) {
        Map<LocalDate, Dia> out = new TreeMap<>();
        Map<LocalDate, Map<String, BigDecimal[]>> somasMedias = new LinkedHashMap<>();
        for (JsonNode item : resp.path("data")) {
            LocalDate dia = diaDo(item, diaUnico);
            if (dia == null) {
                continue;
            }
            Dia d = out.computeIfAbsent(dia, Dia::new);
            String status = item.path("groupByKey").path("value").path("orderStatus").asText("");
            int n = contagem(item);
            BigDecimal gmv = metrica(item, "gmv", "sum");
            if ("CANCELLED".equalsIgnoreCase(status)) {
                d.cancelados += n;
                d.gmvCancelado = d.gmvCancelado.add(gmv);
                continue;
            }
            d.pedidos += n;
            d.gmv = d.gmv.add(gmv);
            BigDecimal inc = metrica(item, "benefitByTypePartnerTotalValue", "sum");
            d.incentivoLoja = (d.incentivoLoja == null ? BigDecimal.ZERO : d.incentivoLoja).add(inc);
            // médias de tempo ponderadas pela quantidade de pedidos de cada status
            Map<String, BigDecimal[]> sm = somasMedias.computeIfAbsent(dia, k -> new LinkedHashMap<>());
            for (String m : METRICAS_MEDIA) {
                JsonNode v = item.path(m).path("avg");
                if (v.isNumber() && n > 0) {
                    BigDecimal[] acc = sm.computeIfAbsent(m, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    acc[0] = acc[0].add(v.decimalValue().multiply(BigDecimal.valueOf(n)));
                    acc[1] = acc[1].add(BigDecimal.valueOf(n));
                }
            }
        }
        somasMedias.forEach((dia, sm) -> {
            Dia d = out.get(dia);
            d.tempoEntrega = media(sm.get("timeToDeliveryInMin"));
            d.tempoPreparo = media(sm.get("preparationTimeInMin"));
            d.atraso = media(sm.get("timeDelayedInMin"));
        });
        return out;
    }

    /** Novos clientes por dia (contagem de customerFirstOrderInMerchant = true). */
    public static Map<LocalDate, Integer> lerNovosClientes(JsonNode resp, LocalDate diaUnico) {
        Map<LocalDate, Integer> out = new TreeMap<>();
        for (JsonNode item : resp.path("data")) {
            LocalDate dia = diaDo(item, diaUnico);
            if (dia == null) {
                continue;
            }
            JsonNode v = item.path("groupByKey").path("value").path("customerFirstOrderInMerchant");
            boolean primeiro = v.isBoolean() ? v.asBoolean() : "true".equalsIgnoreCase(v.asText());
            out.merge(dia, primeiro ? contagem(item) : 0, Integer::sum);
        }
        return out;
    }

    /** Linhas da curva (dia da semana × hora). */
    public static List<Hora> lerCurva(JsonNode resp) {
        List<Hora> out = new ArrayList<>();
        for (JsonNode item : resp.path("data")) {
            JsonNode v = item.path("groupByKey").path("value");
            int dia = diaSemana(v.path("dayOfWeek"));
            int hora = v.path("orderCreationHour").asInt(-1);
            if (dia < 1 || hora < 0 || hora > 23) {
                continue;
            }
            out.add(new Hora(dia, hora, metrica(item, "gmv", "sum"), contagem(item)));
        }
        return out;
    }

    /** true se a resposta veio com um item por dia (dateRangeKey presente). */
    public static boolean temDatas(JsonNode resp) {
        for (JsonNode item : resp.path("data")) {
            if (item.has("dateRangeKey")) {
                return true;
            }
        }
        return false;
    }

    // ----------------------------------------------------------------- apoio

    private static LocalDate diaDo(JsonNode item, LocalDate diaUnico) {
        JsonNode from = item.path("dateRangeKey").path("value").path("from");
        if (from.isTextual() && from.asText().length() >= 10) {
            return LocalDate.parse(from.asText().substring(0, 10));
        }
        return diaUnico;
    }

    /** Quantidade de pedidos do item: groupByKey.count, senão dateRangeKey.count. */
    static int contagem(JsonNode item) {
        JsonNode g = item.path("groupByKey").path("count");
        if (g.isNumber()) {
            return g.asInt();
        }
        JsonNode d = item.path("dateRangeKey").path("count");
        return d.isNumber() ? d.asInt() : 0;
    }

    static BigDecimal metrica(JsonNode item, String campo, String agg) {
        JsonNode v = item.path(campo).path(agg);
        return v.isNumber() ? v.decimalValue().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    private static BigDecimal media(BigDecimal[] acc) {
        if (acc == null || acc[1].signum() == 0) {
            return null;
        }
        return acc[0].divide(acc[1], 2, RoundingMode.HALF_UP);
    }

    /** dayOfWeek vem como número (1=seg) ou como nome ("MONDAY"). */
    static int diaSemana(JsonNode v) {
        if (v.isNumber()) {
            return v.asInt();
        }
        String s = v.asText("");
        if (s.matches("\\d+")) {
            return Integer.parseInt(s);
        }
        try {
            return DayOfWeek.valueOf(s.toUpperCase()).getValue();
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }
}
