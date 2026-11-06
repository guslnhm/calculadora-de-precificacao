-- Etapa 6: reajuste em massa dos preços no iFood a partir da calculadora

-- 1) Uma loja da calculadora pode ter várias lojas no iFood (rede): tira a unicidade de loja_id
DO $$
DECLARE r record;
BEGIN
    FOR r IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        WHERE t.relname = 'loja_ifood' AND c.contype = 'u'
          AND c.conkey = ARRAY[(SELECT a.attnum FROM pg_attribute a WHERE a.attrelid = t.oid AND a.attname = 'loja_id')]::smallint[]
    LOOP
        EXECUTE 'ALTER TABLE loja_ifood DROP CONSTRAINT ' || quote_ident(r.conname);
    END LOOP;
END $$;
CREATE INDEX IF NOT EXISTS ix_loja_ifood_loja ON loja_ifood (loja_id);

-- 2) Qual item da calculadora dá o preço de cada item/complemento do iFood
CREATE TABLE IF NOT EXISTS item_ifood_vinculo (
    id             bigserial PRIMARY KEY,
    merchant_id    text        NOT NULL,
    tipo           text        NOT NULL,          -- ITEM | COMPLEMENTO
    ifood_id       text        NOT NULL,
    ifood_nome     text,
    item_id        bigint REFERENCES itens (id) ON DELETE CASCADE,  -- null quando ignorado
    ignorado       boolean     NOT NULL DEFAULT false,              -- "não reajustar este"
    origem         text        NOT NULL,          -- AUTO | MANUAL
    similaridade   numeric(4, 3),
    atualizado_em  timestamptz NOT NULL,
    atualizado_por text,
    CONSTRAINT uk_item_ifood_vinculo UNIQUE (merchant_id, tipo, ifood_id)
);
CREATE INDEX IF NOT EXISTS ix_item_ifood_vinculo_item ON item_ifood_vinculo (item_id);

-- 3) Cada publicação de reajuste (para acompanhar o progresso e desfazer tudo de uma vez)
CREATE TABLE IF NOT EXISTS reajuste_lote (
    id           bigserial PRIMARY KEY,
    loja_id      bigint      NOT NULL,
    usuario      text,
    criado_em    timestamptz NOT NULL,
    situacao     text        NOT NULL,            -- PUBLICANDO | CONCLUIDO | DESFAZENDO | DESFEITO
    fonte        text,                            -- CALCULADO | SALVO
    total        int         NOT NULL DEFAULT 0,
    feitas       int         NOT NULL DEFAULT 0,
    falhas       int         NOT NULL DEFAULT 0,
    desfeitas    int         NOT NULL DEFAULT 0,
    terminado_em timestamptz,
    desfeito_em  timestamptz,
    desfeito_por text
);
CREATE INDEX IF NOT EXISTS ix_reajuste_lote_loja ON reajuste_lote (loja_id, criado_em DESC);

ALTER TABLE cardapio_alteracao ADD COLUMN IF NOT EXISTS lote_id bigint REFERENCES reajuste_lote (id);
CREATE INDEX IF NOT EXISTS ix_cardapio_alteracao_lote ON cardapio_alteracao (lote_id);
