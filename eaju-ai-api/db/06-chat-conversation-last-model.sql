-- 会话记录上次选用的模型提供方与 mode，便于刷新后恢复 UI
-- psql -U postgres -h localhost -d eaju_ai -f db/06-chat-conversation-last-model.sql

ALTER TABLE chat_conversation
    ADD COLUMN IF NOT EXISTS last_provider_code VARCHAR(64);

ALTER TABLE chat_conversation
    ADD COLUMN IF NOT EXISTS last_mode_key VARCHAR(512);

COMMENT ON COLUMN chat_conversation.last_provider_code IS '最近一次发消息时选用的 llm_provider_config.code';
COMMENT ON COLUMN chat_conversation.last_mode_key IS '最近一次发消息时选用的 modes_json 键（逻辑 mode）';
