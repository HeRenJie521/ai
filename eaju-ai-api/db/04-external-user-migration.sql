-- =============================================================================
-- 从旧版「app_user + BIGINT user_id」迁到「仅手机号 VARCHAR user_id」
-- 仅在已部署过旧 db/03-user-and-conversation.sql 时执行一次。
-- =============================================================================

ALTER TABLE chat_conversation DROP CONSTRAINT IF EXISTS chat_conversation_user_id_fkey;

ALTER TABLE chat_conversation
    ALTER COLUMN user_id TYPE VARCHAR(32)
    USING CASE
        WHEN user_id IS NULL THEN ''
        ELSE trim(user_id::text)
    END;

DROP TABLE IF EXISTS app_user CASCADE;
