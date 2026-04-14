-- =============================================================================
-- 会话列表 chat_conversation：user_id 存业务侧手机号（无外键，与外部用户体系对齐）
-- 新环境执行：psql -U postgres -h localhost -d eaju_ai -f db/03-chat-conversation.sql
-- 若曾执行过旧版含 app_user 的脚本，请先执行 db/04-external-user-migration.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS chat_conversation (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              VARCHAR(32)  NOT NULL,
    session_id           VARCHAR(128) NOT NULL,
    title                VARCHAR(200) NOT NULL DEFAULT '新对话',
    last_message_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_provider_code   VARCHAR(64),
    last_mode_key        VARCHAR(512),
    UNIQUE (user_id, session_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_conversation_user_last
    ON chat_conversation (user_id, last_message_at DESC);

COMMENT ON TABLE chat_conversation IS '左侧会话列表；user_id 为登录接口返回的手机号；与 Redis chat:session:{sessionId}、chat_turn.user_id 一致';
