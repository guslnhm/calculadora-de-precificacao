-- Etapa 3: monitor de lojas do iFood e alertas no WhatsApp.

-- Lojas que autorizaram o aplicativo no iFood (lista atualizada pelo monitor)
CREATE TABLE IF NOT EXISTS ifood_merchant (
    merchant_id   VARCHAR(64) PRIMARY KEY,
    nome          VARCHAR(200),
    razao_social  VARCHAR(200),
    ativo         BOOLEAN NOT NULL DEFAULT TRUE,   -- false quando some da lista do token
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Situação atual de cada operação/canal de cada loja (1 linha por chave)
CREATE TABLE IF NOT EXISTS ifood_status_atual (
    merchant_id   VARCHAR(64) NOT NULL,
    operacao      VARCHAR(40) NOT NULL,
    canal         VARCHAR(40) NOT NULL,
    estado        VARCHAR(20) NOT NULL,
    motivos       JSONB NOT NULL DEFAULT '[]',
    mensagem      TEXT,
    desde         TIMESTAMPTZ NOT NULL,             -- desde quando está neste estado
    verificado_em TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (merchant_id, operacao, canal)
);

-- Histórico: grava só quando o estado ou os motivos mudam
CREATE TABLE IF NOT EXISTS ifood_status_historico (
    id           BIGSERIAL PRIMARY KEY,
    merchant_id  VARCHAR(64) NOT NULL,
    operacao     VARCHAR(40) NOT NULL,
    canal        VARCHAR(40) NOT NULL,
    estado       VARCHAR(20) NOT NULL,
    estado_ifood VARCHAR(20),
    disponivel   BOOLEAN,
    motivos      JSONB NOT NULL DEFAULT '[]',
    mensagem     TEXT,
    reabrivel    BOOLEAN,
    bruto        JSONB,
    mudou_em     TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_status_historico_loja ON ifood_status_historico (merchant_id, mudou_em DESC);

-- Incidentes: período em que uma operação ficou em estado de alerta
CREATE TABLE IF NOT EXISTS ifood_incidente (
    id                BIGSERIAL PRIMARY KEY,
    merchant_id       VARCHAR(64) NOT NULL,
    operacao          VARCHAR(40) NOT NULL,
    canal             VARCHAR(40) NOT NULL,
    estado            VARCHAR(20) NOT NULL,
    motivos           JSONB NOT NULL DEFAULT '[]',
    mensagem          TEXT,
    reabrivel         BOOLEAN,
    pausas            JSONB,                         -- pausas ativas quando o incidente abriu
    inicio            TIMESTAMPTZ NOT NULL,          -- 1º ciclo em que o problema apareceu
    confirmado_em     TIMESTAMPTZ NOT NULL,          -- quando o alerta foi disparado
    fim               TIMESTAMPTZ,
    estado_final      VARCHAR(20),
    ultimo_lembrete_em TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_incidente_aberto
    ON ifood_incidente (merchant_id, operacao, canal) WHERE fim IS NULL;
CREATE INDEX IF NOT EXISTS ix_incidente_inicio ON ifood_incidente (inicio DESC);

-- Grupos de WhatsApp e o mapa grupo -> lojas (uma loja pode estar em vários grupos)
CREATE TABLE IF NOT EXISTS whatsapp_grupo (
    id              BIGSERIAL PRIMARY KEY,
    nome            VARCHAR(120) NOT NULL UNIQUE,
    chat_id         VARCHAR(120) NOT NULL,           -- Z-API: 1203...-group
    todas_lojas     BOOLEAN NOT NULL DEFAULT FALSE,
    admin           BOOLEAN NOT NULL DEFAULT FALSE,  -- recebe avisos do monitor e lojas sem grupo
    avisar_retorno  BOOLEAN NOT NULL DEFAULT TRUE,
    ativo           BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS whatsapp_grupo_loja (
    grupo_id    BIGINT NOT NULL REFERENCES whatsapp_grupo (id) ON DELETE CASCADE,
    merchant_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (grupo_id, merchant_id)
);

-- Cada mensagem enviada (ou que falhou, ou que só foi registrada no modo sombra)
CREATE TABLE IF NOT EXISTS whatsapp_envio (
    id           BIGSERIAL PRIMARY KEY,
    tipo         VARCHAR(30) NOT NULL,     -- alerta | monitor | resumo | teste
    grupo        VARCHAR(120),
    chat_id      VARCHAR(120),
    incidentes   BIGINT[],
    corpo        TEXT NOT NULL,
    situacao     VARCHAR(20) NOT NULL,     -- ENVIADO | FALHOU | SOMBRA
    erro         TEXT,
    enviado_em   TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_whatsapp_envio_data ON whatsapp_envio (enviado_em DESC);
