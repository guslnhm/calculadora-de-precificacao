-- Etapa 1 da integração com o iFood.

-- 1) Regras de preço do iFood por loja
--    ifood_frete_padrao:   frete somado ao CMV (quase sempre zero)
--    ifood_modo_cupom:     qual valor vai para o iFood: BASE, CUPOM_5, CUPOM_8 ou CUPOM_10
--    ifood_arredondamento: NENHUM (padrão), NOVENTA (,90) ou NOVENTA_E_NOVE (,99)
ALTER TABLE lojas ADD COLUMN IF NOT EXISTS ifood_frete_padrao NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE lojas ADD COLUMN IF NOT EXISTS ifood_modo_cupom VARCHAR(20) NOT NULL DEFAULT 'BASE';
ALTER TABLE lojas ADD COLUMN IF NOT EXISTS ifood_arredondamento VARCHAR(20) NOT NULL DEFAULT 'NENHUM';

-- 2) Frete específico de um item (NULL = usa o frete padrão da loja)
ALTER TABLE itens ADD COLUMN IF NOT EXISTS frete NUMERIC(12, 2);

-- 3) Vínculo entre a loja da calculadora e a loja no iFood (merchantId)
CREATE TABLE IF NOT EXISTS loja_ifood (
    id BIGSERIAL PRIMARY KEY,
    loja_id BIGINT NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    nome_ifood VARCHAR(200),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_loja_ifood_loja FOREIGN KEY (loja_id) REFERENCES lojas (id),
    CONSTRAINT uk_loja_ifood_loja UNIQUE (loja_id),
    CONSTRAINT uk_loja_ifood_merchant UNIQUE (merchant_id)
);
