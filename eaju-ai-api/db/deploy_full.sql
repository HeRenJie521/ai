-- =============================================================================
-- 智蚁 AI API - 完整数据库部署脚本（无事务版本）
-- 版本：1.4.0（模型管理优化版）
-- 数据库：PostgreSQL 12+
-- 执行方式：psql -U postgres -h 主机地址 -d 数据库名 -f deploy_full.sql
-- =============================================================================

-- =============================================================================
-- 第一部分：创建数据表
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1.1 API Key 表（集成管理）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS api_key (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(128) NOT NULL,
    secret_hash         VARCHAR(64)  NOT NULL,
    secret_prefix       VARCHAR(64)  NOT NULL,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    type                INTEGER      NOT NULL DEFAULT 1,
    default_model_id    BIGINT,
    allowed_origins     VARCHAR(1000),
    welcome_text        TEXT,
    suggestions         TEXT,
    system_role         TEXT,
    system_task         TEXT,
    system_constraints  TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_api_key_secret_hash ON api_key (secret_hash);
CREATE INDEX IF NOT EXISTS idx_api_key_default_model_id ON api_key (default_model_id);

COMMENT ON TABLE api_key IS '开放 API 调用密钥 / 嵌入网站集成配置；明文仅创建时返回一次，库中仅存 SHA-256';
COMMENT ON COLUMN api_key.type IS '集成类型：1=API_KEY（默认） 2=WEB_EMBED（嵌入网站）';
COMMENT ON COLUMN api_key.default_model_id IS '默认模型 ID，关联 llm_model.id';
COMMENT ON COLUMN api_key.allowed_origins IS 'WEB_EMBED：允许嵌入的来源域名，逗号分隔；为空表示不限';
COMMENT ON COLUMN api_key.welcome_text IS 'WEB_EMBED：开场白文本';
COMMENT ON COLUMN api_key.suggestions IS 'WEB_EMBED：推荐问题 JSON 字符串，例如：["问题 1", "问题 2"]';
COMMENT ON COLUMN api_key.system_role IS 'WEB_EMBED：Agent 角色设定';
COMMENT ON COLUMN api_key.system_task IS 'WEB_EMBED：Agent 任务指令';
COMMENT ON COLUMN api_key.system_constraints IS 'WEB_EMBED：Agent 限制条件';

-- -----------------------------------------------------------------------------
-- 1.2 对话轮次表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_turn (
    id                      BIGSERIAL PRIMARY KEY,
    session_id              VARCHAR(128),
    user_id                 VARCHAR(128),
    provider                VARCHAR(64)  NOT NULL,
    model                   VARCHAR(256),
    client_messages_json    TEXT,
    request_messages_json   TEXT,
    assistant_content       TEXT,
    reasoning_content       TEXT,
    upstream_message_id     VARCHAR(128),
    finish_reason           VARCHAR(64),
    prompt_tokens           INTEGER,
    completion_tokens       INTEGER,
    total_tokens            INTEGER,
    stream_mode             BOOLEAN      NOT NULL DEFAULT FALSE,
    api_key_id              BIGINT,
    integration_id          BIGINT,
    app_id                  BIGINT,
    llm_model_id            BIGINT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_turn_session_id ON chat_turn (session_id);
CREATE INDEX IF NOT EXISTS idx_chat_turn_created_at ON chat_turn (created_at);
CREATE INDEX IF NOT EXISTS idx_chat_turn_api_key_id ON chat_turn (api_key_id);
CREATE INDEX IF NOT EXISTS idx_chat_turn_integration_id ON chat_turn (integration_id);
CREATE INDEX IF NOT EXISTS idx_chat_turn_app_id ON chat_turn (app_id);
CREATE INDEX IF NOT EXISTS idx_chat_turn_llm_model_id ON chat_turn (llm_model_id);

COMMENT ON TABLE chat_turn IS '单次对话轮次：记录用户侧消息、完整请求上下文、助手回复、token 与上游元数据';
COMMENT ON COLUMN chat_turn.api_key_id IS '若请求使用 X-API-Key 鉴权则记录对应密钥 id（type=1）';
COMMENT ON COLUMN chat_turn.integration_id IS '集成 ID：type=2 时等于 api_key_id，用于按集成统计';
COMMENT ON COLUMN chat_turn.app_id IS 'AI 应用嵌入 ID（ai_app.id），用于按应用统计用量';
COMMENT ON COLUMN chat_turn.llm_model_id IS '本轮使用的模型 ID，关联 llm_model.id';

-- -----------------------------------------------------------------------------
-- 1.3 会话列表表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_conversation (
    id                        BIGSERIAL PRIMARY KEY,
    user_id                   VARCHAR(32)  NOT NULL,
    session_id                VARCHAR(128) NOT NULL,
    title                     VARCHAR(200) NOT NULL DEFAULT '新对话',
    last_message_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_provider_code        VARCHAR(64),
    last_provider_display_name VARCHAR(128),
    last_mode_key             VARCHAR(512),
    api_key_id                BIGINT,
    integration_id            BIGINT,
    app_id                    BIGINT,
    llm_model_id              BIGINT,
    deleted_at                TIMESTAMPTZ,
    UNIQUE (user_id, session_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_conversation_user_last
    ON chat_conversation (user_id, last_message_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_conversation_api_key_id ON chat_conversation (api_key_id);
CREATE INDEX IF NOT EXISTS idx_chat_conversation_integration_id ON chat_conversation (integration_id);
CREATE INDEX IF NOT EXISTS idx_chat_conversation_app_id ON chat_conversation (app_id);
CREATE INDEX IF NOT EXISTS idx_chat_conversation_llm_model_id ON chat_conversation (llm_model_id);
CREATE INDEX IF NOT EXISTS idx_chat_conversation_deleted_at ON chat_conversation (deleted_at);
CREATE INDEX IF NOT EXISTS idx_chat_conversation_last_provider_display_name ON chat_conversation (last_provider_display_name);

COMMENT ON TABLE chat_conversation IS '左侧会话列表；user_id 为登录接口返回的手机号';
COMMENT ON COLUMN chat_conversation.api_key_id IS '会话归属的 API Key（type=1）';
COMMENT ON COLUMN chat_conversation.integration_id IS '集成 ID（type=2）';
COMMENT ON COLUMN chat_conversation.app_id IS 'AI 应用嵌入 ID（ai_app.id），用于按应用统计';
COMMENT ON COLUMN chat_conversation.llm_model_id IS '会话最后使用的模型 ID，关联 llm_model.id';
COMMENT ON COLUMN chat_conversation.last_provider_display_name IS '最后使用的模型提供商显示名称（如百炼、DeepSeek）';
COMMENT ON COLUMN chat_conversation.deleted_at IS '逻辑删除时间，非空表示已删除';

-- -----------------------------------------------------------------------------
-- 1.4 大模型提供方配置表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS llm_provider_config (
    id                      BIGSERIAL PRIMARY KEY,
    code                    VARCHAR(64)  NOT NULL UNIQUE,
    display_name            VARCHAR(128) NOT NULL,
    api_key                 TEXT         NOT NULL DEFAULT '',
    base_url                TEXT         NOT NULL,
    enabled                 BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order              INTEGER      NOT NULL DEFAULT 0,
    force_temperature       NUMERIC(5,2),
    thinking_param_style    VARCHAR(32)  NOT NULL DEFAULT 'openai',
    json_mode_system_hint   BOOLEAN      NOT NULL DEFAULT FALSE,
    strip_tool_call_index   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_llm_provider_config_enabled_sort
    ON llm_provider_config (enabled, sort_order);

COMMENT ON TABLE llm_provider_config IS 'OpenAI 兼容 Chat Completions 的提供方配置';
COMMENT ON COLUMN llm_provider_config.code IS '与 POST /chat 请求体 provider 对应';
COMMENT ON COLUMN llm_provider_config.display_name IS '提供方显示名称（如：通义千问、DeepSeek）';
COMMENT ON COLUMN llm_provider_config.force_temperature IS '非空时强制覆盖请求中的 temperature（如 Kimi 只接受 1.0）';
COMMENT ON COLUMN llm_provider_config.thinking_param_style IS 'thinking 参数风格：openai（thinking.type=...）或 dashscope（enable_thinking=true/false）';
COMMENT ON COLUMN llm_provider_config.json_mode_system_hint IS '使用 JSON 模式时是否自动在 system message 中注入 "json" 关键词';
COMMENT ON COLUMN llm_provider_config.strip_tool_call_index IS '将历史 tool_calls 回传给模型前是否剥掉 index 字段';

-- -----------------------------------------------------------------------------
-- 1.5 大模型实例配置表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS llm_model (
    id                    BIGSERIAL    PRIMARY KEY,
    provider_id           BIGINT       NOT NULL REFERENCES llm_provider_config(id),
    name                  VARCHAR(256) NOT NULL,
    upstream_model_id     VARCHAR(256) NOT NULL,
    text_generation       BOOLEAN      NOT NULL DEFAULT TRUE,
    deep_thinking         BOOLEAN      NOT NULL DEFAULT FALSE,
    vision                BOOLEAN      NOT NULL DEFAULT FALSE,
    stream_output         BOOLEAN      NOT NULL DEFAULT TRUE,
    tool_call             BOOLEAN      NOT NULL DEFAULT TRUE,
    force_thinking_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    temperature           NUMERIC(5,2),
    max_tokens            INTEGER,
    top_p                 NUMERIC(5,2),
    top_k                 INTEGER,
    frequency_penalty     NUMERIC(5,2),
    presence_penalty      NUMERIC(5,2),
    response_format       VARCHAR(64),
    thinking_mode         BOOLEAN,
    context_window        INTEGER,
    sort_order            INTEGER      NOT NULL DEFAULT 0,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (provider_id, name)
);

CREATE INDEX IF NOT EXISTS idx_llm_model_provider_id ON llm_model (provider_id);
CREATE INDEX IF NOT EXISTS idx_llm_model_enabled_sort ON llm_model (enabled, sort_order);

COMMENT ON TABLE llm_model IS '大模型实例配置（每个模型单独配置）';
COMMENT ON COLUMN llm_model.name IS '逻辑名（即 /chat 请求体 mode 参数值，也是前端展示名称）';
COMMENT ON COLUMN llm_model.upstream_model_id IS '实际发往上游 API 的 model 字段值';
COMMENT ON COLUMN llm_model.text_generation IS '是否支持文本生成';
COMMENT ON COLUMN llm_model.deep_thinking IS '是否支持深度思考';
COMMENT ON COLUMN llm_model.vision IS '是否支持视觉理解（图片）';
COMMENT ON COLUMN llm_model.stream_output IS '是否支持流式输出';
COMMENT ON COLUMN llm_model.tool_call IS '是否支持工具调用（Function Calling）';
COMMENT ON COLUMN llm_model.force_thinking_enabled IS '是否强制开启 thinking（如 MiniMax 系列）';
COMMENT ON COLUMN llm_model.supports_thinking_api IS '是否支持 thinking API 调用';
COMMENT ON COLUMN llm_model.response_format IS 'TEXT 或 JSON_OBJECT';

-- -----------------------------------------------------------------------------
-- 1.6 AI 应用表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_app (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(128) NOT NULL,
    welcome_text        TEXT,
    suggestions         TEXT,
    system_role         TEXT,
    system_task         TEXT,
    system_constraints  TEXT,
    llm_model_id        BIGINT REFERENCES llm_model(id),
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_app_llm_model_id ON ai_app (llm_model_id);
CREATE INDEX IF NOT EXISTS idx_ai_app_deleted ON ai_app (deleted);

COMMENT ON TABLE ai_app IS 'AI 应用配置';
COMMENT ON COLUMN ai_app.llm_model_id IS '应用绑定的模型 ID，关联 llm_model.id';
COMMENT ON COLUMN ai_app.welcome_text IS '开场白文本';
COMMENT ON COLUMN ai_app.suggestions IS '推荐问题 JSON 字符串';
COMMENT ON COLUMN ai_app.system_role IS 'Agent 角色设定';
COMMENT ON COLUMN ai_app.system_task IS 'Agent 任务指令';
COMMENT ON COLUMN ai_app.system_constraints IS 'Agent 限制条件';

-- -----------------------------------------------------------------------------
-- 1.7 AI 工具表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_tool (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(128) NOT NULL,
    label               VARCHAR(128) NOT NULL,
    description         TEXT,
    http_method         VARCHAR(16)  NOT NULL DEFAULT 'GET',
    url                 TEXT         NOT NULL,
    headers_json        TEXT,
    body_template       TEXT,
    content_type        VARCHAR(128),
    method_name         VARCHAR(128),
    data_params_json    TEXT,
    response_params_json TEXT,
    params_schema_json  TEXT,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_tool_enabled ON ai_tool (enabled);

COMMENT ON TABLE ai_tool IS 'AI 工具（Function Calling）';
COMMENT ON COLUMN ai_tool.name IS '工具标识（英文）';
COMMENT ON COLUMN ai_tool.label IS '工具显示名称（中文）';
COMMENT ON COLUMN ai_tool.data_params_json IS '入参配置 JSON';
COMMENT ON COLUMN ai_tool.response_params_json IS '出参配置 JSON';
COMMENT ON COLUMN ai_tool.params_schema_json IS '参数 Schema JSON（用于 LLM 工具定义）';

-- -----------------------------------------------------------------------------
-- 1.8 应用 - 工具绑定表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_app_tool (
    id              BIGSERIAL PRIMARY KEY,
    app_id          BIGINT       NOT NULL REFERENCES ai_app(id),
    tool_id         BIGINT       NOT NULL REFERENCES ai_tool(id),
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    call_strategy   VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_app_tool_app_id ON ai_app_tool (app_id);
CREATE INDEX IF NOT EXISTS idx_ai_app_tool_tool_id ON ai_app_tool (tool_id);

COMMENT ON TABLE ai_app_tool IS 'AI 应用与工具的绑定关系';
COMMENT ON COLUMN ai_app_tool.call_strategy IS '调用策略说明，例如：当用户询问{{相关场景}}时调用此工具（最大 500 字符）';

-- -----------------------------------------------------------------------------
-- 1.9 接口定义表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS api_definition (
    id              BIGSERIAL PRIMARY KEY,
    system_name     VARCHAR(128) NOT NULL,
    request_url     TEXT         NOT NULL,
    http_method     VARCHAR(16)  NOT NULL DEFAULT 'GET',
    content_type    VARCHAR(128),
    remark          TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE api_definition IS '接口定义（供工具调用参考）';

-- -----------------------------------------------------------------------------
-- 1.10 用户上下文字段表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_context_field (
    id                  BIGSERIAL PRIMARY KEY,
    field_key           VARCHAR(128) NOT NULL,
    label               VARCHAR(128) NOT NULL,
    field_type          VARCHAR(32)  NOT NULL DEFAULT 'String',
    parse_expression    VARCHAR(512),
    description         TEXT,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_context_field_enabled ON user_context_field (enabled);

COMMENT ON TABLE user_context_field IS '用户上下文字段配置';
COMMENT ON COLUMN user_context_field.field_type IS '字段类型：String、Number、Boolean、Object、Array';
COMMENT ON COLUMN user_context_field.parse_expression IS '解析表达式（dot-notation 路径）';

-- =============================================================================
-- 第二部分：种子数据
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 2.1 大模型提供方配置
-- -----------------------------------------------------------------------------
INSERT INTO llm_provider_config (code, display_name, api_key, base_url, enabled, sort_order, thinking_param_style, json_mode_system_hint)
VALUES
    (
        'DEEPSEEK',
        'DeepSeek',
        '',
        'https://api.deepseek.com/v1',
        TRUE,
        10,
        'openai',
        FALSE
    ),
    (
        'QWEN',
        '通义千问',
        '',
        'https://dashscope.aliyuncs.com/compatible-mode/v1',
        TRUE,
        20,
        'dashscope',
        TRUE
    ),
    (
        'KIMI',
        '月之暗面 Kimi',
        '',
        'https://api.moonshot.cn/v1',
        TRUE,
        30,
        'openai',
        FALSE
    ),
    (
        'GEMINI',
        'Google Gemini',
        '',
        'https://generativelanguage.googleapis.com/v1beta/openai/',
        TRUE,
        40,
        'openai',
        FALSE
    ),
    (
        'QIANFAN',
        '百度千帆',
        '',
        'https://qianfan.baidubce.com/v2',
        TRUE,
        50,
        'openai',
        FALSE
    ),
    (
        'MINIMAX',
        'MiniMax',
        '',
        'https://api.minimax.io/v1',
        TRUE,
        60,
        'openai',
        FALSE
    );

-- 设置 Kimi 强制 temperature=1.0
UPDATE llm_provider_config SET force_temperature = 1.0 WHERE code = 'KIMI';

-- -----------------------------------------------------------------------------
-- 2.2 大模型实例配置
-- -----------------------------------------------------------------------------

-- DeepSeek 模型
INSERT INTO llm_model (provider_id, name, upstream_model_id, text_generation, deep_thinking, vision, stream_output, tool_call, supports_thinking_api, temperature, max_tokens, top_p, sort_order, enabled)
SELECT 
    p.id,
    v.name,
    v.upstream_model_id,
    TRUE,
    v.deep_thinking,
    FALSE,
    TRUE,
    TRUE,
    v.supports_thinking_api,
    0.7,
    4096,
    0.95,
    v.sort_order,
    TRUE
FROM llm_provider_config p,
LATERAL (
    VALUES 
        ('deepseek-chat', 'deepseek-chat', FALSE, FALSE, 10),
        ('deepseek-reasoner', 'deepseek-reasoner', TRUE, TRUE, 20)
) AS v(name, upstream_model_id, deep_thinking, supports_thinking_api, sort_order)
WHERE p.code = 'DEEPSEEK';

-- 通义千问模型
INSERT INTO llm_model (provider_id, name, upstream_model_id, text_generation, deep_thinking, vision, stream_output, tool_call, supports_thinking_api, temperature, max_tokens, top_p, sort_order, enabled)
SELECT 
    p.id,
    v.name,
    v.upstream_model_id,
    TRUE,
    v.deep_thinking,
    v.vision,
    TRUE,
    TRUE,
    v.supports_thinking_api,
    0.7,
    4096,
    0.95,
    v.sort_order,
    TRUE
FROM llm_provider_config p,
LATERAL (
    VALUES 
        ('qwen3.6-plus', 'qwen3.6-plus', FALSE, TRUE, FALSE, 10),
        ('qwen3.5-plus', 'qwen3.5-plus', FALSE, TRUE, FALSE, 20),
        ('qwen3.5-flash', 'qwen3.5-flash', FALSE, FALSE, FALSE, 30)
) AS v(name, upstream_model_id, deep_thinking, vision, supports_thinking_api, sort_order)
WHERE p.code = 'QWEN';

-- Kimi 模型
INSERT INTO llm_model (provider_id, name, upstream_model_id, text_generation, deep_thinking, vision, stream_output, tool_call, supports_thinking_api, temperature, max_tokens, top_p, sort_order, enabled)
SELECT 
    p.id,
    v.name,
    v.upstream_model_id,
    TRUE,
    v.deep_thinking,
    FALSE,
    TRUE,
    TRUE,
    v.supports_thinking_api,
    1.0,
    4096,
    0.95,
    v.sort_order,
    TRUE
FROM llm_provider_config p,
LATERAL (
    VALUES 
        ('kimi-k2.5', 'kimi-k2.5', FALSE, FALSE, 10),
        ('kimi-k2-turbo-preview', 'kimi-k2-turbo-preview', FALSE, FALSE, 20),
        ('kimi-k2-thinking', 'kimi-k2-thinking', TRUE, TRUE, 30)
) AS v(name, upstream_model_id, deep_thinking, supports_thinking_api, sort_order)
WHERE p.code = 'KIMI';

-- Gemini 模型
INSERT INTO llm_model (provider_id, name, upstream_model_id, text_generation, deep_thinking, vision, stream_output, tool_call, supports_thinking_api, temperature, max_tokens, top_p, sort_order, enabled)
SELECT 
    p.id,
    v.name,
    v.upstream_model_id,
    TRUE,
    FALSE,
    v.vision,
    TRUE,
    TRUE,
    FALSE,
    0.7,
    8192,
    0.95,
    v.sort_order,
    TRUE
FROM llm_provider_config p,
LATERAL (
    VALUES 
        ('gemini-2.5-pro', 'gemini-2.5-pro', FALSE, TRUE, 10),
        ('gemini-2.5-flash', 'gemini-2.5-flash', FALSE, FALSE, 20),
        ('gemini-3.1-flash-lite-preview', 'gemini-3.1-flash-lite-preview', FALSE, FALSE, 30)
) AS v(name, upstream_model_id, vision, sort_order)
WHERE p.code = 'GEMINI';

-- 百度千帆模型
INSERT INTO llm_model (provider_id, name, upstream_model_id, text_generation, deep_thinking, vision, stream_output, tool_call, supports_thinking_api, temperature, max_tokens, top_p, sort_order, enabled)
SELECT 
    p.id,
    v.name,
    v.upstream_model_id,
    TRUE,
    FALSE,
    FALSE,
    TRUE,
    TRUE,
    FALSE,
    0.7,
    4096,
    0.95,
    v.sort_order,
    TRUE
FROM llm_provider_config p,
LATERAL (
    VALUES 
        ('ernie-4.0-turbo-128k', 'ernie-4.0-turbo-128k', 10),
        ('ernie-3.5-128k', 'ernie-3.5-128k', 20),
        ('ernie-speed-pro-128k', 'ernie-speed-pro-128k', 30)
) AS v(name, upstream_model_id, sort_order)
WHERE p.code = 'QIANFAN';

-- MiniMax 模型
INSERT INTO llm_model (provider_id, name, upstream_model_id, text_generation, deep_thinking, vision, stream_output, tool_call, force_thinking_enabled, supports_thinking_api, temperature, max_tokens, top_p, sort_order, enabled)
SELECT 
    p.id,
    v.name,
    v.upstream_model_id,
    TRUE,
    FALSE,
    FALSE,
    TRUE,
    TRUE,
    v.force_thinking,
    FALSE,
    0.7,
    4096,
    0.95,
    v.sort_order,
    TRUE
FROM llm_provider_config p,
LATERAL (
    VALUES 
        ('MiniMax-M2.7', 'MiniMax-M2.7', FALSE, 10),
        ('MiniMax-M2.5', 'MiniMax-M2.5', FALSE, 20),
        ('MiniMax-M2.1', 'MiniMax-M2.1', FALSE, 30),
        ('MiniMax-M1', 'MiniMax-M1', TRUE, 40)
) AS v(name, upstream_model_id, force_thinking, sort_order)
WHERE p.code = 'MINIMAX';

-- =============================================================================
-- 第三部分：使用说明
-- =============================================================================
-- 
-- 1. 更新 API Key（执行以下 SQL 更新）：
--    UPDATE llm_provider_config SET api_key = '你的 DeepSeek 密钥' WHERE code = 'DEEPSEEK';
--    UPDATE llm_provider_config SET api_key = '你的通义千问密钥' WHERE code = 'QWEN';
--    UPDATE llm_provider_config SET api_key = '你的 Kimi 密钥' WHERE code = 'KIMI';
--    UPDATE llm_provider_config SET api_key = '你的 Gemini 密钥' WHERE code = 'GEMINI';
--    UPDATE llm_provider_config SET api_key = '你的千帆密钥' WHERE code = 'QIANFAN';
--    UPDATE llm_provider_config SET api_key = '你的 MiniMax 密钥' WHERE code = 'MINIMAX';
--
-- 2. 验证表结构：
--    \d api_key
--    \d chat_turn
--    \d chat_conversation
--    \d llm_provider_config
--    \d llm_model
--    \d ai_app
--    \d ai_tool
--    \d ai_app_tool
--
-- =============================================================================
