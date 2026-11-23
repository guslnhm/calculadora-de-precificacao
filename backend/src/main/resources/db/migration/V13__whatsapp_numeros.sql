-- Mais de um número de WhatsApp enviando: cada número é uma instância da Evolution.
-- O número principal continua sendo o da variável EVOLUTION_INSTANCIA; aqui ficam os outros.
CREATE TABLE IF NOT EXISTS whatsapp_numero (
    instancia  VARCHAR(60)  PRIMARY KEY,
    nome       VARCHAR(120) NOT NULL,
    criado_em  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Número que envia para o grupo (NULL = o principal)
ALTER TABLE whatsapp_grupo ADD COLUMN IF NOT EXISTS instancia VARCHAR(60);
