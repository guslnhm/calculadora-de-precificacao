-- 99Food: lojas vinculadas ao app (Open Platform) e lançamentos do financeiro (Get Bill Data), um por
-- pedido/tipo de lançamento. Os valores vêm em centavos, como a 99 manda; o JSON original fica guardado
-- para recalcular se a regra de soma mudar.
CREATE TABLE IF NOT EXISTS food99_loja (
    shop_id        VARCHAR(32)  PRIMARY KEY,
    app_shop_id    VARCHAR(64),
    nome           VARCHAR(200),
    vinculada      BOOLEAN      NOT NULL DEFAULT FALSE,
    ultima_coleta  TIMESTAMPTZ,
    erro           TEXT,
    atualizado_em  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE IF NOT EXISTS food99_lancamento (
    shop_id            VARCHAR(32)  NOT NULL,
    order_id           VARCHAR(40)  NOT NULL,
    order_type         SMALLINT     NOT NULL,
    business_ts        BIGINT       NOT NULL,
    dia                DATE         NOT NULL,
    delivery_type      SMALLINT,
    valor_itens        BIGINT,      -- mealOriginalAmount
    desconto_loja      BIGINT,      -- shopActivityOutcome + freeDeliveryOutcome (negativos)
    subsidio_99        BIGINT,      -- shopActivitySubsidy + freeDeliverySubsidy
    entrega_loja       BIGINT,      -- shopDeliveryAmount
    comissao           BIGINT,      -- commissionAmount + commissionSubsidyAmount
    taxa_pagamento     BIGINT,      -- payCommissionAmount
    taxa_entrega       BIGINT,      -- b2pDeliveryAmount
    valor_pedido       BIGINT,      -- orderAmount
    liquido            BIGINT,      -- settlementAmount
    vale_refeicao      BIGINT,      -- mealVoucherAmount
    dinheiro           BIGINT,      -- cashBalance
    cancel_reason      INT,
    payment_channel    INT,
    previsao_repasse   VARCHAR(20),
    bruto              JSONB        NOT NULL,
    coletado_em        TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (shop_id, order_id, order_type, business_ts)
);

CREATE INDEX IF NOT EXISTS food99_lancamento_dia ON food99_lancamento (dia, shop_id);
