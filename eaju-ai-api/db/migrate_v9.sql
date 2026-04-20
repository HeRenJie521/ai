-- =============================================================================
-- 智蚁 AI API - 数据库迁移脚本 V9
-- 版本：1.4.0（模型管理优化）
-- 数据库：PostgreSQL 12+
-- 说明：
--   1. api_key 表增加 default_model_id 字段，关联 llm_model.id
--   2. chat_conversation 表增加 last_provider_display_name 字段
--   3. 数据迁移：将旧数据迁移到新字段
-- 执行方式：psql -U postgres -h 主机地址 -d 数据库名 -f migrate_v9.sql
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. api_key 表增加 default_model_id 字段
-- -----------------------------------------------------------------------------
ALTER TABLE api_key 
    ADD COLUMN IF NOT EXISTS default_model_id BIGINT;

COMMENT ON COLUMN api_key.default_model_id IS '默认模型 ID，关联 llm_model.id';

-- 添加外键约束（如果不存在）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_api_key_default_model_id' 
        AND table_name = 'api_key'
    ) THEN
        ALTER TABLE api_key 
            ADD CONSTRAINT fk_api_key_default_model_id 
            FOREIGN KEY (default_model_id) 
            REFERENCES llm_model(id);
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 2. chat_conversation 表增加 last_provider_display_name 字段
-- -----------------------------------------------------------------------------
ALTER TABLE chat_conversation 
    ADD COLUMN IF NOT EXISTS last_provider_display_name VARCHAR(128);

COMMENT ON COLUMN chat_conversation.last_provider_display_name IS '最后使用的模型提供商显示名称（如百炼、DeepSeek）';

-- -----------------------------------------------------------------------------
-- 3. 数据迁移：更新现有记录的 last_provider_display_name
-- -----------------------------------------------------------------------------
-- 根据 last_provider_code 更新 last_provider_display_name
UPDATE chat_conversation c
SET last_provider_display_name = p.display_name
FROM llm_provider_config p
WHERE c.last_provider_code = p.code
  AND c.last_provider_display_name IS NULL;

-- -----------------------------------------------------------------------------
-- 4. 创建索引
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_api_key_default_model_id 
    ON api_key (default_model_id);

CREATE INDEX IF NOT EXISTS idx_chat_conversation_last_provider_display_name 
    ON chat_conversation (last_provider_display_name);

COMMIT;
