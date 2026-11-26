-- Taxa de entrega paga pelo cliente (Analytics: netDeliveryFee), para separar o valor dos itens do faturamento:
-- valor dos itens = gmv - taxa_entrega + incentivo_loja (conferido contra o relatório de pedidos).
ALTER TABLE painel_dia ADD COLUMN IF NOT EXISTS taxa_entrega NUMERIC(14,2);

-- Relê o histórico (dias-historico) de todas as lojas na próxima coleta, para preencher a coluna nova.
UPDATE painel_coleta SET historico_ok = FALSE;
