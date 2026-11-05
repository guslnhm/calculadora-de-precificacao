-- Tabelas base (lojas, itens, usuarios).
--
-- Até aqui elas eram criadas pelo Hibernate (ddl-auto: update), que roda DEPOIS
-- do Flyway. Num banco novo, a V1 falhava porque referencia lojas(id) antes de a
-- tabela existir. Esta migração cria as tabelas com IF NOT EXISTS:
--   - banco novo: cria tudo, e a V1 em diante funciona;
--   - banco que já existe: não faz nada (as tabelas já estão lá).
-- Como a versão (0.9) é menor que as já aplicadas, ela depende de
-- spring.flyway.out-of-order=true no application.yaml.

CREATE TABLE IF NOT EXISTS lojas (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    cliente_nome VARCHAR(150),
    observacao VARCHAR(255),
    percentual_imposto NUMERIC(5, 2),
    percentual_custo_fixo NUMERIC(5, 2),
    percentual_taxa_franquia NUMERIC(5, 2),
    percentual_taxa_transacao NUMERIC(5, 2),
    percentual_taxa_ifood NUMERIC(5, 2),
    percentual_taxa_repasse NUMERIC(5, 2),
    percentual_lucro NUMERIC(5, 2),
    ativo BOOLEAN NOT NULL,
    criado_em TIMESTAMP NOT NULL,
    atualizado_em TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS itens (
    id BIGSERIAL PRIMARY KEY,
    loja_id BIGINT NOT NULL REFERENCES lojas (id),
    nome_item VARCHAR(150) NOT NULL,
    cmv NUMERIC(12, 2) NOT NULL,
    preco_venda_inicial NUMERIC(12, 2),
    rendimento NUMERIC(12, 2),
    observacao VARCHAR(255),
    preco_venda_atual NUMERIC(12, 2),
    preco_venda_atual_ifood NUMERIC(12, 2),
    preco_venda_atual_99food NUMERIC(12, 2),
    data_precificacao TIMESTAMP,
    ativo BOOLEAN NOT NULL,
    criado_em TIMESTAMP NOT NULL,
    atualizado_em TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS usuarios (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    email VARCHAR(180) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    perfil VARCHAR(20) NOT NULL,
    ativo BOOLEAN NOT NULL,
    criado_em TIMESTAMP NOT NULL,
    atualizado_em TIMESTAMP NOT NULL
);
