-- =============================================================================
-- 智蚁 AI API - 数据库迁移脚本 V10
-- 版本：1.5.0（模型配置简化）
-- 数据库：PostgreSQL 12+
-- 说明：
--   1. 删除 llm_model 表的 supports_thinking_api 字段
--   2. 深度思考字段同时表示是否支持 thinking API
-- 执行方式：psql -U postgres -h 主机地址 -d 数据库名 -f migrate_v10.sql
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. 删除 supports_thinking_api 字段
-- -----------------------------------------------------------------------------
ALTER TABLE llm_model 
    DROP COLUMN IF EXISTS supports_thinking_api;

COMMENT ON COLUMN llm_model.deep_thinking IS '是否支持深度思考（同时表示支持 thinking API）';

COMMIT;
