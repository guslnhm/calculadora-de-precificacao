-- O resumo de 19/11/2026 saiu com "0 pedidos" (o iFood atrasou o D-1). Marca como pendente de correção,
-- para ele ser reenviado quando os números chegarem. Só vale se a migração rodar no próprio dia 19/11.
INSERT INTO whatsapp_envio (tipo, corpo, situacao, enviado_em)
SELECT 'resumo-pendente', '(resumo de 19/11 enviado sem os números do iFood)', 'SOMBRA', now()
WHERE (now() AT TIME ZONE 'America/Sao_Paulo')::date = DATE '2026-11-19';
