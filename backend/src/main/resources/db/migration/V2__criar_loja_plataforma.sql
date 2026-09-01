CREATE TABLE loja_plataforma (
    id BIGSERIAL PRIMARY KEY,

    loja_id BIGINT NOT NULL,
    plataforma VARCHAR(30) NOT NULL,

    ativo BOOLEAN NOT NULL DEFAULT TRUE,

    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_loja_plataforma_loja
        FOREIGN KEY (loja_id)
        REFERENCES lojas(id),

    CONSTRAINT uk_loja_plataforma_vinculo
        UNIQUE (loja_id, plataforma)
);

-- Preserva o comportamento atual:
-- todas as lojas já existentes continuam aparecendo no iFood.
INSERT INTO loja_plataforma (
    loja_id,
    plataforma,
    ativo
)
SELECT
    id,
    'IFOOD',
    TRUE
FROM lojas
ON CONFLICT (loja_id, plataforma) DO NOTHING;

-- Se alguma loja já tiver configuração da 99Food,
-- ela também passa automaticamente a estar vinculada à 99Food.
INSERT INTO loja_plataforma (
    loja_id,
    plataforma,
    ativo
)
SELECT DISTINCT
    loja_id,
    'FOOD99',
    TRUE
FROM loja_configuracao_plataforma
WHERE plataforma = 'FOOD99'
  AND ativo = TRUE
ON CONFLICT (loja_id, plataforma) DO NOTHING;