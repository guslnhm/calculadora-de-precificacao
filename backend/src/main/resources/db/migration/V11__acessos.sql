-- Acessos: senha com BCrypt (as antigas são convertidas no próximo login), sessões no banco,
-- convites por link, lojas de cada usuário e registro de uso (adesão).

ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS data_nascimento  DATE;
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS ultimo_acesso_em TIMESTAMPTZ;
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS convite_id       BIGINT;
CREATE INDEX IF NOT EXISTS ix_usuarios_email_lower ON usuarios (lower(email));

-- Lojas que um usuário MENTORADO pode ver (chave do painel: merchant_id do iFood ou "bi:<id>")
CREATE TABLE IF NOT EXISTS usuario_loja (
    usuario_id BIGINT      NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    chave      VARCHAR(80) NOT NULL,
    PRIMARY KEY (usuario_id, chave)
);

CREATE TABLE IF NOT EXISTS convite (
    id          BIGSERIAL PRIMARY KEY,
    token       VARCHAR(64)  NOT NULL UNIQUE,
    descricao   VARCHAR(200),
    perfil      VARCHAR(20)  NOT NULL DEFAULT 'MENTORADO',
    lojas       TEXT[]       NOT NULL DEFAULT '{}',
    max_usos    INT          NOT NULL DEFAULT 1,
    usos        INT          NOT NULL DEFAULT 0,
    criado_por  VARCHAR(120),
    criado_em   TIMESTAMPTZ  NOT NULL,
    expira_em   TIMESTAMPTZ  NOT NULL,
    revogado_em TIMESTAMPTZ
);

-- Sessão de login (guardamos só o hash do token)
CREATE TABLE IF NOT EXISTS sessao (
    token_hash    VARCHAR(64) PRIMARY KEY,
    usuario_id    BIGINT      NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    criado_em     TIMESTAMPTZ NOT NULL,
    expira_em     TIMESTAMPTZ NOT NULL,
    ultimo_uso_em TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_sessao_usuario ON sessao (usuario_id);

-- Uso por dia (adesão)
CREATE TABLE IF NOT EXISTS usuario_atividade (
    usuario_id  BIGINT      NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    dia         DATE        NOT NULL,
    primeiro    TIMESTAMPTZ NOT NULL,
    ultimo      TIMESTAMPTZ NOT NULL,
    requisicoes INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (usuario_id, dia)
);

CREATE TABLE IF NOT EXISTS usuario_login (
    id         BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT      NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    quando     TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_usuario_login ON usuario_login (usuario_id, quando DESC);

-- Senha fraca herdada (texto puro, curta): pede troca no próximo acesso
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS trocar_senha BOOLEAN NOT NULL DEFAULT FALSE;

-- Link para criar uma senha nova (quem esqueceu a senha)
CREATE TABLE IF NOT EXISTS senha_link (
    token_hash VARCHAR(64) PRIMARY KEY,
    usuario_id BIGINT      NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    criado_em  TIMESTAMPTZ NOT NULL,
    expira_em  TIMESTAMPTZ NOT NULL,
    usado_em   TIMESTAMPTZ
);
