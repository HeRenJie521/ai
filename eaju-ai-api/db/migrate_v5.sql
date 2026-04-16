-- migrate_v5.sql
-- 为 chat_turn 和 chat_conversation 添加 app_id 字段，用于追踪 AI 应用嵌入会话的用量。

ALTER TABLE chat_turn ADD COLUMN IF NOT EXISTS app_id BIGINT;
ALTER TABLE chat_conversation ADD COLUMN IF NOT EXISTS app_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_chat_turn_app_id ON chat_turn(app_id);
CREATE INDEX IF NOT EXISTS idx_chat_conversation_app_id ON chat_conversation(app_id);
