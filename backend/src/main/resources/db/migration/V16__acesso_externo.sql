-- Acesso de sistemas de fora (BI de metas etc.) aos números diários do Painel, só das lojas liberadas.
-- A chave em si não é guardada: só o SHA-256 dela e o começo (para reconhecer na tela).
CREATE TABLE IF NOT EXISTS acesso_externo (
    id          BIGSERIAL    PRIMARY KEY,
    nome        VARCHAR(120) NOT NULL,
    chave_hash  CHAR(64)     NOT NULL UNIQUE,
    prefixo     VARCHAR(12)  NOT NULL,
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_por  VARCHAR(120),
    criado_em   TIMESTAMPTZ  NOT NULL,
    ultimo_uso  TIMESTAMPTZ,
    usos        BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS acesso_externo_loja (
    acesso_id   BIGINT      NOT NULL REFERENCES acesso_externo (id) ON DELETE CASCADE,
    merchant_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (acesso_id, merchant_id)
);
