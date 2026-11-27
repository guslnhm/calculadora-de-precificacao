package com.precificacao.precificacao.externo;

import com.precificacao.precificacao.painel.base.BaseHistoricaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Lê da base histórica (public.pedidos, carregada com o relatório de pedidos do Portal). */
@Component
public class ItensDoRelatorioBase implements ItensDoRelatorio {

    private static final Logger log = LoggerFactory.getLogger(ItensDoRelatorioBase.class);

    private final BaseHistoricaService base;

    public ItensDoRelatorioBase(BaseHistoricaService base) {
        this.base = base;
    }

    @Override
    public Map<String, Valores> buscar(Collection<String> idsLoja, LocalDate de, LocalDate ate) {
        Map<String, Valores> out = new HashMap<>();
        if (idsLoja.isEmpty() || !base.configurada()) {
            return out;
        }
        try {
            base.naBase(c -> {
                try (PreparedStatement ps = c.prepareStatement("""
                        SELECT p.id_loja::text, p.data_hora_pedido::date AS dia,
                               coalesce(sum(p.valor_itens - CASE WHEN p.status_final_pedido = 'CANCELAMENTO PARCIAL'
                                                                 THEN coalesce(p.valor_itens_cancelados, 0) ELSE 0 END)
                                   FILTER (WHERE p.status_final_pedido <> 'CANCELADO'), 0) AS itens,
                               coalesce(sum(coalesce(p.taxa_entrega_cliente, 0))
                                   FILTER (WHERE p.status_final_pedido <> 'CANCELADO'), 0) AS taxa,
                               count(*) FILTER (WHERE p.status_final_pedido = 'CANCELADO' AND p.valor_liquido > 0) AS canc_pagos,
                               coalesce(sum(p.valor_itens)
                                   FILTER (WHERE p.status_final_pedido = 'CANCELADO' AND p.valor_liquido > 0), 0) AS itens_canc_pagos
                        FROM public.pedidos p
                        WHERE p.id_loja::text = ANY (?)
                          AND p.data_hora_pedido >= ? AND p.data_hora_pedido < ?
                        GROUP BY 1, 2""")) {
                    ps.setArray(1, c.createArrayOf("text", idsLoja.toArray()));
                    ps.setTimestamp(2, Timestamp.valueOf(de.atStartOfDay()));
                    ps.setTimestamp(3, Timestamp.valueOf(ate.plusDays(1).atStartOfDay()));
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            out.put(ItensDoRelatorio.chave(rs.getString(1), rs.getDate(2).toLocalDate()),
                                    new Valores(rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getInt(5), rs.getBigDecimal(6)));
                        }
                    }
                }
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("Acesso externo: não consegui ler itens do relatório na base: {}", e.getMessage());
        }
        return out;
    }
}
