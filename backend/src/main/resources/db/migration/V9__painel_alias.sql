-- Como cada operação do BI aparece no iFood (nome e short id da loja), copiado de silver.dim_operacao_alias.
CREATE TABLE IF NOT EXISTS painel_operacao_alias (
    id_operacao INT          NOT NULL,
    short_id    VARCHAR(30),
    nome_origem VARCHAR(200),
    PRIMARY KEY (id_operacao, short_id)
);

-- Short id (o "ID" curto do Portal) de cada loja, descoberto pelo Analytics.
CREATE TABLE IF NOT EXISTS painel_short_id (
    merchant_id   VARCHAR(64) PRIMARY KEY,
    short_id      VARCHAR(30) NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL
);
