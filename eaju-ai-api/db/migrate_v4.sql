-- =============================================================================
-- 智蚁 AI API - v4.0 迁移脚本
-- 目标：移除 api_key 表中的 app_id 字段（API Key 无需关联 AI 应用）
-- 数据库：PostgreSQL 12+
-- 执行方式：psql -U postgres -h 主机地址 -d 数据库名 -f migrate_v4.sql
-- =============================================================================

BEGIN;

ALTER TABLE api_key DROP COLUMN IF EXISTS app_id;

COMMIT;
