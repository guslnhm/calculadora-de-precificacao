CREATE TABLE loja_configuracao_plataforma (
    id BIGSERIAL PRIMARY KEY,

    loja_id BIGINT NOT NULL,
    plataforma VARCHAR(30) NOT NULL,

    percentual_imposto NUMERIC(10, 4) NOT NULL DEFAULT 0,
    percentual_custo_fixo NUMERIC(10, 4) NOT NULL DEFAULT 0,
    percentual_taxa_plataforma NUMERIC(10, 4) NOT NULL DEFAULT 0,
    percentual_taxa_franquia NUMERIC(10, 4) NOT NULL DEFAULT 0,
    percentual_taxa_transacao NUMERIC(10, 4) NOT NULL DEFAULT 0,
    percentual_taxa_antecipacao NUMERIC(10, 4) NOT NULL DEFAULT 0,
    percentual_lucro NUMERIC(10, 4) NOT NULL DEFAULT 0,

    ativo BOOLEAN NOT NULL DEFAULT TRUE,

    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_configuracao_plataforma_loja
        FOREIGN KEY (loja_id)
        REFERENCES lojas(id),

    CONSTRAINT uk_loja_plataforma
        UNIQUE (loja_id, plataforma)
);