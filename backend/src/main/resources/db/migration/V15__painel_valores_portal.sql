-- Valores do Portal do Parceiro para calcular taxa de repasse e custo da plataforma no painel
ALTER TABLE painel_bi_periodo ADD COLUMN IF NOT EXISTS recebido_via_loja NUMERIC(16, 2);
ALTER TABLE painel_bi_periodo ADD COLUMN IF NOT EXISTS repassado_plataforma NUMERIC(16, 2);
ALTER TABLE painel_manual ADD COLUMN IF NOT EXISTS valor_bruto NUMERIC(16, 2);
ALTER TABLE painel_manual ADD COLUMN IF NOT EXISTS valor_liquido NUMERIC(16, 2);
ALTER TABLE painel_manual ADD COLUMN IF NOT EXISTS recebido_via_loja NUMERIC(16, 2);
ALTER TABLE painel_manual ADD COLUMN IF NOT EXISTS repassado_plataforma NUMERIC(16, 2);
