-- Marca de alteração de cardápio (feita pela consultoria) importada da gold, para o ponto no gráfico do boletim.
ALTER TABLE painel_bi_periodo ADD COLUMN IF NOT EXISTS alteracao_cardapio BOOLEAN NOT NULL DEFAULT FALSE;
