-- =============================================================================
-- 智蚁 AI API - v3.0 迁移脚本
-- 目标：移除 ai_app 表中的 temperature 字段（采样温度由模型配置统一管理）
-- 数据库：PostgreSQL 12+
-- 执行方式：psql -U postgres -h 主机地址 -d 数据库名 -f migrate_v3.sql
-- =============================================================================

BEGIN;

ALTER TABLE ai_app DROP COLUMN IF EXISTS temperature;

COMMIT;
