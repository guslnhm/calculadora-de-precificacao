package com.precificacao.precificacao.food99;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Repository
public class Food99Repository {

    /** Um dia de uma loja, em centavos (como a 99 manda). */
    public record Dia(String shopId, String loja, LocalDate dia, int pedidos, int cancelados, long valorItens,
                      long descontoLoja, long subsidio99, long entregaLoja, long comissao, long taxaPagamento,
                      long taxaEntrega, long outrosLancamentos, long liquido, long valeRefeicao, long dinheiro) {
    }

    private final JdbcTemplate jdbc;

    public Food99Repository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void salvarLojas(List<Food99Client.Loja> lojas, OffsetDateTime agora) {
        for (Food99Client.Loja l : lojas) {
            jdbc.update("""
                    INSERT INTO food99_loja (shop_id, app_shop_id, nome, vinculada, atualizado_em) VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (shop_id) DO UPDATE SET app_shop_id = EXCLUDED.app_shop_id, nome = EXCLUDED.nome,
                        vinculada = EXCLUDED.vinculada, atualizado_em = EXCLUDED.atualizado_em""",
                    l.shopId(), l.appShopId(), l.nome(), l.vinculada(), agora);
        }
    }

    public void coletaOk(String shopId, OffsetDateTime agora) {
        jdbc.update("UPDATE food99_loja SET ultima_coleta = ?, erro = NULL WHERE shop_id = ?", agora, shopId);
    }

    public void coletaErro(String shopId, String erro) {
        jdbc.update("UPDATE food99_loja SET erro = ? WHERE shop_id = ?", erro, shopId);
    }

    public List<Map<String, Object>> lojas() {
        return jdbc.queryForList("""
                SELECT l.shop_id, l.app_shop_id, l.nome, l.vinculada, l.erro,
                       to_char(l.ultima_coleta AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') AS ultima_coleta,
                       (SELECT max(dia)::text FROM food99_lancamento x WHERE x.shop_id = l.shop_id) AS ultimo_dia
                FROM food99_loja l ORDER BY l.vinculada DESC, l.nome""");
    }

    /** Grava (ou atualiza) os lançamentos de uma loja. */
    @Transactional
    public int salvarLancamentos(String shopId, List<JsonNode> itens, ZoneId zona, OffsetDateTime agora) {
        int n = 0;
        for (JsonNode i : itens) {
            long ts = i.path("businessTs").asLong();
            LocalDate dia = Instant.ofEpochSecond(ts).atZone(zona).toLocalDate();
            String loja = i.hasNonNull("shopId") ? i.path("shopId").asText() : shopId;
            n += jdbc.update("""
                    INSERT INTO food99_lancamento (shop_id, order_id, order_type, business_ts, dia, delivery_type,
                        valor_itens, desconto_loja, subsidio_99, entrega_loja, comissao, taxa_pagamento, taxa_entrega,
                        valor_pedido, liquido, vale_refeicao, dinheiro, cancel_reason, payment_channel, previsao_repasse,
                        bruto, coletado_em)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?)
                    ON CONFLICT (shop_id, order_id, order_type, business_ts) DO UPDATE SET
                        dia = EXCLUDED.dia, delivery_type = EXCLUDED.delivery_type, valor_itens = EXCLUDED.valor_itens,
                        desconto_loja = EXCLUDED.desconto_loja, subsidio_99 = EXCLUDED.subsidio_99,
                        entrega_loja = EXCLUDED.entrega_loja, comissao = EXCLUDED.comissao,
                        taxa_pagamento = EXCLUDED.taxa_pagamento, taxa_entrega = EXCLUDED.taxa_entrega,
                        valor_pedido = EXCLUDED.valor_pedido, liquido = EXCLUDED.liquido,
                        vale_refeicao = EXCLUDED.vale_refeicao, dinheiro = EXCLUDED.dinheiro,
                        cancel_reason = EXCLUDED.cancel_reason, payment_channel = EXCLUDED.payment_channel,
                        previsao_repasse = EXCLUDED.previsao_repasse, bruto = EXCLUDED.bruto,
                        coletado_em = EXCLUDED.coletado_em""",
                    loja, i.path("orderId").asText(), i.path("orderType").asInt(), ts, Date.valueOf(dia),
                    i.path("deliveryType").asInt(), v(i, "mealOriginalAmount"),
                    v(i, "shopActivityOutcome") + v(i, "freeDeliveryOutcome"),
                    v(i, "shopActivitySubsidy") + v(i, "freeDeliverySubsidy"), v(i, "shopDeliveryAmount"),
                    v(i, "commissionAmount") + v(i, "commissionSubsidyAmount"), v(i, "payCommissionAmount"),
                    v(i, "b2pDeliveryAmount"), v(i, "orderAmount"), v(i, "settlementAmount"),
                    v(i, "mealVoucherAmount"), v(i, "cashBalance"), i.path("cancelReason").asInt(),
                    i.path("paymentChannel").asInt(), i.path("expectSettleDate").asText(null), i.toString(), agora);
        }
        return n;
    }

    private static long v(JsonNode i, String campo) {
        return i.path(campo).asLong(0);
    }

    /**
     * Soma por loja e dia. Pedido = lançamento tipo 1 (receita) sem estorno tipo 2 (cancelado); os valores do pedido
     * (itens, descontos, comissões, taxas) são só dos pedidos que valeram. O líquido soma todos os lançamentos do dia
     * (inclui estornos e despesas fora de pedido, como anúncios), que é o que entra no repasse.
     */
    public List<Dia> dias(LocalDate de, LocalDate ate) {
        return jdbc.query("""
                WITH canc AS (SELECT DISTINCT shop_id, order_id FROM food99_lancamento WHERE order_type = 2),
                l AS (
                  SELECT x.*, (x.order_type = 1 AND c.order_id IS NULL) AS valeu
                  FROM food99_lancamento x LEFT JOIN canc c ON c.shop_id = x.shop_id AND c.order_id = x.order_id
                  WHERE x.dia BETWEEN ? AND ?)
                SELECT l.shop_id, COALESCE(lj.nome, l.shop_id) AS loja, l.dia,
                       count(DISTINCT l.order_id) FILTER (WHERE l.valeu) AS pedidos,
                       count(DISTINCT l.order_id) FILTER (WHERE l.order_type = 2) AS cancelados,
                       COALESCE(sum(l.valor_itens) FILTER (WHERE l.valeu), 0) AS valor_itens,
                       COALESCE(sum(l.desconto_loja) FILTER (WHERE l.valeu), 0) AS desconto_loja,
                       COALESCE(sum(l.subsidio_99) FILTER (WHERE l.valeu), 0) AS subsidio_99,
                       COALESCE(sum(l.entrega_loja) FILTER (WHERE l.valeu), 0) AS entrega_loja,
                       COALESCE(sum(l.comissao) FILTER (WHERE l.valeu), 0) AS comissao,
                       COALESCE(sum(l.taxa_pagamento) FILTER (WHERE l.valeu), 0) AS taxa_pagamento,
                       COALESCE(sum(l.taxa_entrega) FILTER (WHERE l.valeu), 0) AS taxa_entrega,
                       COALESCE(sum(l.liquido) FILTER (WHERE l.order_type NOT IN (1, 2)), 0) AS outros,
                       COALESCE(sum(l.liquido), 0) AS liquido,
                       COALESCE(sum(l.vale_refeicao) FILTER (WHERE l.valeu), 0) AS vale_refeicao,
                       COALESCE(sum(l.dinheiro) FILTER (WHERE l.valeu), 0) AS dinheiro
                FROM l LEFT JOIN food99_loja lj ON lj.shop_id = l.shop_id
                GROUP BY l.shop_id, lj.nome, l.dia
                ORDER BY l.dia, loja""", (rs, n) -> new Dia(rs.getString("shop_id"), rs.getString("loja"),
                rs.getDate("dia").toLocalDate(), rs.getInt("pedidos"), rs.getInt("cancelados"), rs.getLong("valor_itens"),
                rs.getLong("desconto_loja"), rs.getLong("subsidio_99"), rs.getLong("entrega_loja"), rs.getLong("comissao"),
                rs.getLong("taxa_pagamento"), rs.getLong("taxa_entrega"), rs.getLong("outros"), rs.getLong("liquido"),
                rs.getLong("vale_refeicao"), rs.getLong("dinheiro")), Date.valueOf(de), Date.valueOf(ate));
    }

    /** Só para testes. */
    void limparTudo() {
        jdbc.update("DELETE FROM food99_lancamento");
        jdbc.update("DELETE FROM food99_loja");
    }
}
