-- =============================================================================
-- 智蚁 AI API - v2.0 架构重构迁移脚本
-- 目标：将 AI 能力配置（ai_app）与接入方式（api_key 集成）解耦
-- 数据库：PostgreSQL 12+
-- 执行方式：psql -U postgres -h 主机地址 -d 数据库名 -f migrate_v2.sql
-- =============================================================================

BEGIN;

-- =============================================================================
-- 第一步：创建 ai_app 表
-- =============================================================================

CREATE TABLE IF NOT EXISTS ai_app (
    id              BIGSERIAL    PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    welcome_text    TEXT,
    suggestions     TEXT,
    system_role     TEXT,
    system_task     TEXT,
    system_constraints TEXT,
    model_id        VARCHAR(256),
    temperature     DECIMAL(4,2),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE ai_app IS 'AI 应用配置：封装开场白、系统提示词、默认模型等 AI 能力，可被多个集成（api_key）复用';
COMMENT ON COLUMN ai_app.model_id IS '默认对话模型 ID（如 deepseek-chat）';
COMMENT ON COLUMN ai_app.temperature IS '采样温度，为空则使用模型默认值';
COMMENT ON COLUMN ai_app.welcome_text IS '开场白文本';
COMMENT ON COLUMN ai_app.suggestions IS '推荐问题 JSON 字符串，例如：["问题 1", "问题 2"]';
COMMENT ON COLUMN ai_app.system_role IS 'Agent 角色设定';
COMMENT ON COLUMN ai_app.system_task IS 'Agent 任务指令';
COMMENT ON COLUMN ai_app.system_constraints IS 'Agent 限制条件';

-- =============================================================================
-- 第二步：api_key 表新增 app_id 列
-- =============================================================================

ALTER TABLE api_key ADD COLUMN IF NOT EXISTS app_id BIGINT;

COMMENT ON COLUMN api_key.app_id IS '关联的 AI 应用 ID（ai_app.id），决定聊天行为（提示词、模型等）';

-- =============================================================================
-- 第三步：移除 api_key 表中已迁移至 ai_app 的字段
-- （如有存量数据需先迁移，见注释说明）
-- =============================================================================

-- 如需保留旧数据，可先执行以下迁移步骤（可选）：
-- INSERT INTO ai_app (name, welcome_text, suggestions, system_role, system_task, system_constraints, model_id, created_at, updated_at)
-- SELECT name, welcome_text, suggestions, system_role, system_task, system_constraints, default_model, created_at, updated_at
-- FROM api_key WHERE type = 2 AND deleted = false;
-- 然后手动将 app_id 关联到上面插入的记录 id。

ALTER TABLE api_key
    DROP COLUMN IF EXISTS default_model,
    DROP COLUMN IF EXISTS welcome_text,
    DROP COLUMN IF EXISTS suggestions,
    DROP COLUMN IF EXISTS system_role,
    DROP COLUMN IF EXISTS system_task,
    DROP COLUMN IF EXISTS system_constraints;

-- =============================================================================
-- 验证
-- =============================================================================

-- 确认 ai_app 表结构
-- \d ai_app

-- 确认 api_key 表结构（应包含 app_id，不再包含 welcome_text 等字段）
-- \d api_key

COMMIT;
