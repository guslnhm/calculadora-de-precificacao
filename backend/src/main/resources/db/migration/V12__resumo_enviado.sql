-- Números que foram no resumo diário de cada loja, para saber se o iFood mudou o dia depois do envio
-- (dia liberado aos poucos) e mandar o resumo "(atualizado)" só para os grupos afetados.
CREATE TABLE IF NOT EXISTS painel_resumo_enviado (
    dia         DATE        NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    movimento   INT         NOT NULL,   -- pedidos + cancelados
    enviado_em  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (dia, merchant_id)
);

-- O resumo de 19/11 saiu às 10h de 20/11 com várias lojas ainda zeradas (o iFood liberou o dia aos poucos).
-- Guarda o que foi enviado (o banco ainda está igual ao envio) para a correção sair quando os números chegarem.
INSERT INTO painel_resumo_enviado (dia, merchant_id, movimento, enviado_em)
SELECT dia, merchant_id, pedidos + cancelados, now()
FROM painel_dia
WHERE dia = DATE '2026-11-19' AND (now() AT TIME ZONE 'America/Sao_Paulo')::date = DATE '2026-11-20'
ON CONFLICT DO NOTHING;
