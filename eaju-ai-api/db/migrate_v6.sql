-- migrate_v6.sql
-- AI 工具调用 + 用户上下文注入系统

-- 用户上下文字段配置表：定义从登录请求中提取哪些字段存入 Redis
CREATE TABLE IF NOT EXISTS user_context_field (
    id          BIGSERIAL PRIMARY KEY,
    field_key   VARCHAR(128) NOT NULL UNIQUE,
    label       VARCHAR(256) NOT NULL,
    description TEXT,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- AI 工具定义表
CREATE TABLE IF NOT EXISTS ai_tool (
    id                 BIGSERIAL PRIMARY KEY,
    name               VARCHAR(128) NOT NULL UNIQUE,
    label              VARCHAR(256) NOT NULL,
    description        TEXT         NOT NULL,
    http_method        VARCHAR(16)  NOT NULL DEFAULT 'POST',
    url                TEXT         NOT NULL,
    headers_json       TEXT,
    body_template      TEXT,
    params_schema_json TEXT         NOT NULL,
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- AI 应用与工具的绑定关系表
CREATE TABLE IF NOT EXISTS ai_app_tool (
    id         BIGSERIAL PRIMARY KEY,
    app_id     BIGINT NOT NULL REFERENCES ai_app(id),
    tool_id    BIGINT NOT NULL REFERENCES ai_tool(id),
    sort_order INT    NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (app_id, tool_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_app_tool_app_id     ON ai_app_tool(app_id);
CREATE INDEX IF NOT EXISTS idx_user_context_field_key  ON user_context_field(field_key);
CREATE INDEX IF NOT EXISTS idx_ai_tool_enabled         ON ai_tool(enabled);
