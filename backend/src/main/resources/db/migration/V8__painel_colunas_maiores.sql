-- O BI antigo tem valores fora do esperado (ex.: taxa_repasse 25046,15). Mesma precisão do BI para não perder linhas.
ALTER TABLE painel_bi_periodo
    ALTER COLUMN valor_itens       TYPE NUMERIC(16,2),
    ALTER COLUMN ticket_medio      TYPE NUMERIC(14,2),
    ALTER COLUMN incentivos_ifood  TYPE NUMERIC(16,2),
    ALTER COLUMN incentivos_loja   TYPE NUMERIC(16,2),
    ALTER COLUMN valor_bruto       TYPE NUMERIC(16,2),
    ALTER COLUMN valor_liquido     TYPE NUMERIC(16,2),
    ALTER COLUMN repasse           TYPE NUMERIC(16,2),
    ALTER COLUMN taxa_repasse      TYPE NUMERIC(14,4),
    ALTER COLUMN conversao         TYPE NUMERIC(14,4),
    ALTER COLUMN tempo_entrega     TYPE NUMERIC(12,2),
    ALTER COLUMN tempo_aberto      TYPE NUMERIC(12,4);
ALTER TABLE painel_manual
    ALTER COLUMN conversao TYPE NUMERIC(14,4),
    ALTER COLUMN repasse   TYPE NUMERIC(16,2);
