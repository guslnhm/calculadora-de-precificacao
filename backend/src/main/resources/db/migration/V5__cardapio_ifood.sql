-- Etapa 5: cardápio do iFood (leitura, monitor de itens pausados e alterações feitas pela tela)

-- Última leitura completa do cardápio de cada loja (é o que a tela mostra)
CREATE TABLE IF NOT EXISTS ifood_cardapio (
    merchant_id text PRIMARY KEY,
    lido_em     timestamptz NOT NULL,
    conteudo    jsonb       NOT NULL,
    itens       int         NOT NULL DEFAULT 0,
    pausados    int         NOT NULL DEFAULT 0,
    erro        text,
    erro_em     timestamptz
);

-- Situação atual de cada item/complemento (1 linha por entidade e catálogo), para detectar mudanças
CREATE TABLE IF NOT EXISTS ifood_item_estado (
    merchant_id      text NOT NULL,
    catalog_id       text NOT NULL,
    tipo             text NOT NULL,              -- ITEM | COMPLEMENTO
    entidade_id      text NOT NULL,
    contexto         text,
    nome             text,
    categoria        text,
    status           text,
    status_categoria text,
    nao_vendavel     text,
    preco            numeric(12, 2),
    problema         text,                       -- PAUSADO | CATEGORIA | NAO_VENDAVEL | null
    desde            timestamptz NOT NULL,
    atualizado_em    timestamptz NOT NULL,
    PRIMARY KEY (merchant_id, catalog_id, tipo, entidade_id)
);

-- Períodos em que um item/complemento ficou pausado ou sem poder ser vendido
CREATE TABLE IF NOT EXISTS ifood_item_pausa (
    id          bigserial PRIMARY KEY,
    merchant_id text        NOT NULL,
    catalog_id  text        NOT NULL,
    tipo        text        NOT NULL,
    entidade_id text        NOT NULL,
    contexto    text,
    nome        text,
    categoria   text,
    problema    text        NOT NULL,
    detalhe     text,
    inicio      timestamptz NOT NULL,
    fim         timestamptz,
    motivo_fim  text,                            -- VOLTOU | REMOVIDO
    ja_estava   boolean     NOT NULL DEFAULT false, -- já estava pausado na 1ª leitura (não avisa)
    avisado     boolean     NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_item_pausa_aberta
    ON ifood_item_pausa (merchant_id, catalog_id, tipo, entidade_id) WHERE fim IS NULL;
CREATE INDEX IF NOT EXISTS ix_item_pausa_inicio ON ifood_item_pausa (inicio DESC);

-- Alterações feitas pela tela (preço/status), com quem fez e como desfazer
CREATE TABLE IF NOT EXISTS cardapio_alteracao (
    id          bigserial PRIMARY KEY,
    merchant_id text        NOT NULL,
    tipo        text        NOT NULL,            -- ITEM | COMPLEMENTO
    entidade_id text        NOT NULL,
    nome        text,
    campo       text        NOT NULL,            -- STATUS | PRECO
    antes       text,
    depois      text        NOT NULL,
    usuario     text,
    feito_em    timestamptz NOT NULL,
    situacao    text        NOT NULL,            -- FEITA | FALHOU | DESFEITA
    erro        text,
    desfeita_em timestamptz,
    desfeita_por text,
    desfaz_id   bigint REFERENCES cardapio_alteracao (id)
);
CREATE INDEX IF NOT EXISTS ix_cardapio_alteracao_loja ON cardapio_alteracao (merchant_id, feito_em DESC);
