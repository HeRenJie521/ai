-- API Key 与 chat关联（与 JPA 实体一致）
CREATE TABLE IF NOT EXISTS api_key (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    secret_hash     VARCHAR(64)  NOT NULL,
    secret_prefix   VARCHAR(32)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_api_key_secret_hash ON api_key (secret_hash);

ALTER TABLE chat_turn ADD COLUMN IF NOT EXISTS api_key_id BIGINT;
ALTER TABLE chat_conversation ADD COLUMN IF NOT EXISTS api_key_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_chat_turn_api_key_id ON chat_turn (api_key_id);
CREATE INDEX IF NOT EXISTS idx_chat_conversation_api_key_id ON chat_conversation (api_key_id);

COMMENT ON TABLE api_key IS '开放 API 调用密钥；明文仅创建时返回一次，库中仅存 SHA-256';
COMMENT ON COLUMN chat_turn.api_key_id IS '若请求使用 X-API-Key 鉴权则记录对应密钥 id';
COMMENT ON COLUMN chat_conversation.api_key_id IS '会话归属的 API Key（与 user_id=apikey:{id} 一致）';
