-- Painel (substitui o Power BI): métricas diárias do Analytics do iFood, curva de vendas por hora,
-- histórico importado do banco do BI e lançamentos manuais (conversão e repasse).

-- Um dia de uma loja (Analytics, D-1)
CREATE TABLE IF NOT EXISTS painel_dia (
    merchant_id    VARCHAR(64)   NOT NULL,
    dia            DATE          NOT NULL,
    pedidos        INT           NOT NULL DEFAULT 0,   -- não cancelados
    cancelados     INT           NOT NULL DEFAULT 0,
    gmv            NUMERIC(14,2) NOT NULL DEFAULT 0,   -- não cancelados
    gmv_cancelado  NUMERIC(14,2) NOT NULL DEFAULT 0,
    novos_clientes INT,
    incentivo_loja NUMERIC(14,2),
    tempo_entrega  NUMERIC(8,2),
    tempo_preparo  NUMERIC(8,2),
    atraso         NUMERIC(8,2),
    coletado_em    TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (merchant_id, dia)
);

-- Venda média por dia da semana (1=seg..7=dom) e hora: base do cálculo de perdas
CREATE TABLE IF NOT EXISTS painel_curva_hora (
    merchant_id   VARCHAR(64)   NOT NULL,
    dia_semana    SMALLINT      NOT NULL,
    hora          SMALLINT      NOT NULL,
    gmv_medio     NUMERIC(14,2) NOT NULL,
    pedidos_medio NUMERIC(10,2) NOT NULL,
    semanas       INT           NOT NULL,
    atualizado_em TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (merchant_id, dia_semana, hora)
);

-- Situação da coleta por loja
CREATE TABLE IF NOT EXISTS painel_coleta (
    merchant_id    VARCHAR(64) PRIMARY KEY,
    ultima_coleta  TIMESTAMPTZ,
    ultimo_erro    TEXT,
    erro_em        TIMESTAMPTZ,
    historico_ok   BOOLEAN NOT NULL DEFAULT FALSE
);

-- Histórico do Power BI (gold.financeiro_periodo), semanal e mensal, iFood e 99Food
CREATE TABLE IF NOT EXISTS painel_bi_periodo (
    id_financeiro     BIGINT PRIMARY KEY,
    id_operacao       INT,
    plataforma        VARCHAR(20) NOT NULL,     -- IFOOD, 99FOOD...
    operacao          VARCHAR(200),
    loja              VARCHAR(200),
    periodo_tipo      VARCHAR(10) NOT NULL,     -- SEMANAL / MENSAL
    data_inicio       DATE NOT NULL,
    data_fim          DATE,
    qtd_pedidos       INT,
    valor_itens       NUMERIC(14,2),
    ticket_medio      NUMERIC(10,2),
    incentivos_ifood  NUMERIC(14,2),
    incentivos_loja   NUMERIC(14,2),
    valor_bruto       NUMERIC(14,2),
    valor_liquido     NUMERIC(14,2),
    repasse           NUMERIC(14,2),
    taxa_repasse      NUMERIC(8,4),
    conversao         NUMERIC(8,4),
    novos_clientes    INT,
    tempo_entrega     NUMERIC(8,2),
    qtd_cancelamentos INT,
    tempo_aberto      NUMERIC(8,4),
    importado_em      TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_bi_periodo_op ON painel_bi_periodo (id_operacao, periodo_tipo, data_inicio);

-- Operação do BI <-> loja do iFood (automático pelo id ou pelo nome; pode ser corrigido na tela)
CREATE TABLE IF NOT EXISTS painel_operacao_vinculo (
    id_operacao   INT PRIMARY KEY,
    merchant_id   VARCHAR(64),
    metodo        VARCHAR(20) NOT NULL,         -- ID, NOME, MANUAL, NENHUM
    atualizado_em TIMESTAMPTZ NOT NULL
);

-- Conversão e repasse lançados a partir do Portal (chave = merchant_id, ou "bi:<id_operacao>")
CREATE TABLE IF NOT EXISTS painel_manual (
    chave          VARCHAR(80) NOT NULL,
    periodo_tipo   VARCHAR(10) NOT NULL,
    data_inicio    DATE        NOT NULL,
    conversao      NUMERIC(8,4),
    repasse        NUMERIC(14,2),
    atualizado_em  TIMESTAMPTZ NOT NULL,
    atualizado_por VARCHAR(120),
    PRIMARY KEY (chave, periodo_tipo, data_inicio)
);
