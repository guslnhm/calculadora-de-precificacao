package com.precificacao.precificacao.externo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/**
 * Valor dos itens e taxa de entrega por loja e dia, tirados do relatório de pedidos do Portal (base histórica).
 * O Analytics só informa o frete já descontado das promoções de entrega (netDeliveryFee), então não dá para
 * separar itens e frete por ele quando a entrega foi grátis.
 */
public interface ItensDoRelatorio {

    record Valores(BigDecimal itens, BigDecimal taxaEntrega) {
    }

    /** Chave: id curto da loja + "|" + dia (yyyy-MM-dd). Só os dias que já estão na base. */
    Map<String, Valores> buscar(Collection<String> idsLoja, LocalDate de, LocalDate ate);

    static String chave(String idLoja, LocalDate dia) {
        return idLoja + "|" + dia;
    }
}
