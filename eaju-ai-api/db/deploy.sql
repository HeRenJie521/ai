-- =============================================================================
-- 智蚁 AI API - 完整数据库部署脚本
-- 版本：1.3.0（含结构化 Agent Prompt 字段）
-- 数据库：PostgreSQL 12+
-- 执行方式：psql -U postgres -h 主机地址 -d 数据库名 -f deploy.sql
-- =============================================================================

-- 开启事务
BEGIN;

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
    default_model       VARCHAR(256),
    allowed_origins     VARCHAR(1000),
    welcome_text        TEXT,
    suggestions         TEXT,
    system_role         TEXT,
    system_task         TEXT,
    system_constraints  TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_api_key_secret_hash ON api_key (secret_hash);

COMMENT ON TABLE api_key IS '开放 API 调用密钥 / 嵌入网站集成配置；明文仅创建时返回一次，库中仅存 SHA-256';
COMMENT ON COLUMN api_key.type IS '集成类型：1=API_KEY（默认） 2=WEB_EMBED（嵌入网站）';
COMMENT ON COLUMN api_key.default_model IS 'WEB_EMBED：默认对话模型 ID（如 deepseek-chat）';
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
    -- 主键：自增唯一标识本条记录
    id                      BIGSERIAL PRIMARY KEY,
    -- 会话 ID：多轮对话分组，与 Redis 键 chat:session:{sessionId} 对应
    session_id              VARCHAR(128),
    -- 业务用户 ID：可选，用于与你们用户体系关联
    user_id                 VARCHAR(128),
    -- 大模型提供方：与 POST /chat 的 provider 字段一致
    provider                VARCHAR(64)  NOT NULL,
    -- 本次调用实际使用的上游模型名
    model                   VARCHAR(256),
    -- 本回合客户端在请求体里传入的 messages 列表的 JSON（不含 Redis 里拼上的历史）
    client_messages_json    TEXT,
    -- 实际发给大模型的完整 messages 的 JSON（含 Redis 会话历史 + 本轮用户消息）
    request_messages_json   TEXT,
    -- 助手回复正文：模型返回的可见回答内容
    assistant_content       TEXT,
    -- 思维链 / 推理内容：如 DeepSeek 思考模式下的 reasoning_content
    reasoning_content       TEXT,
    -- 上游返回的消息 ID：如 OpenAI 兼容协议里的 id
    upstream_message_id     VARCHAR(128),
    -- 结束原因：如 stop、length 等
    finish_reason           VARCHAR(64),
    -- 提示侧 token 数
    prompt_tokens           INTEGER,
    -- 生成侧 token 数
    completion_tokens       INTEGER,
    -- 总 token 数
    total_tokens            INTEGER,
    -- 是否流式调用
    stream_mode             BOOLEAN      NOT NULL DEFAULT FALSE,
    -- 使用 X-API-Key 调用时记录
    api_key_id              BIGINT,
    -- WEB_EMBED 集成 ID，用于按集成统计用量
    integration_id          BIGINT,
    -- 记录创建时间
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_turn_session_id ON chat_turn (session_id);
CREATE INDEX IF NOT EXISTS idx_chat_turn_created_at ON chat_turn (created_at);
CREATE INDEX IF NOT EXISTS idx_chat_turn_api_key_id ON chat_turn (api_key_id);
CREATE INDEX IF NOT EXISTS idx_chat_turn_integration_id ON chat_turn (integration_id);

COMMENT ON TABLE chat_turn IS '单次对话轮次：记录用户侧消息、完整请求上下文、助手回复、token 与上游元数据';
COMMENT ON COLUMN chat_turn.api_key_id IS '若请求使用 X-API-Key 鉴权则记录对应密钥 id（type=1）';
COMMENT ON COLUMN chat_turn.integration_id IS '集成 ID：type=2 时等于 api_key_id，用于按集成统计';

-- -----------------------------------------------------------------------------
-- 1.3 会话列表表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_conversation (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              VARCHAR(32)  NOT NULL,
    session_id           VARCHAR(128) NOT NULL,
    title                VARCHAR(200) NOT NULL DEFAULT '新对话',
    last_message_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_provider_code   VARCHAR(64),
    last_mode_key        VARCHAR(512),
    api_key_id           BIGINT,
    integration_id       BIGINT,
    last_model           VARCHAR(256),
    context_window       INTEGER,
    deleted_at           TIMESTAMPTZ,
    UNIQUE (user_id, session_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_conversation_user_last
    ON chat_conversation (user_id, last_message_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_conversation_api_key_id ON chat_conversation (api_key_id);
CREATE INDEX IF NOT EXISTS idx_chat_conversation_integration_id ON chat_conversation (integration_id);
CREATE INDEX IF NOT EXISTS idx_chat_conversation_deleted_at ON chat_conversation (deleted_at);

COMMENT ON TABLE chat_conversation IS '左侧会话列表；user_id 为登录接口返回的手机号';
COMMENT ON COLUMN chat_conversation.api_key_id IS '会话归属的 API Key（type=1）';
COMMENT ON COLUMN chat_conversation.integration_id IS '集成 ID（type=2）';
COMMENT ON COLUMN chat_conversation.last_model IS '会话最后一次使用的模型 ID';
COMMENT ON COLUMN chat_conversation.context_window IS '会话上下文窗口大小限制';
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
    default_mode            VARCHAR(256) NOT NULL,
    modes_json              TEXT         NOT NULL,
    inference_defaults_json TEXT,
    enabled                 BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order              INTEGER      NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE llm_provider_config IS 'OpenAI 兼容 Chat Completions 的提供方配置';
COMMENT ON COLUMN llm_provider_config.code IS '与 POST /chat 请求体 provider 对应';
COMMENT ON COLUMN llm_provider_config.modes_json IS '逻辑 mode -> 上游 model id，JSON 对象';
CREATE INDEX IF NOT EXISTS idx_llm_provider_config_enabled_sort
    ON llm_provider_config (enabled, sort_order);

COMMENT ON COLUMN llm_provider_config.inference_defaults_json IS '默认推理参数 JSON';

-- =============================================================================
-- 第二部分：种子数据（大模型提供方配置）
-- =============================================================================

INSERT INTO llm_provider_config (code, display_name, api_key, base_url, default_mode, modes_json, inference_defaults_json, enabled, sort_order)
VALUES
(
    'DEEPSEEK',
    'DeepSeek',
    '',
    'https://api.deepseek.com/v1',
    'deepseek-chat',
    '{"deepseek-chat":"deepseek-chat","deepseek-reasoner":"deepseek-reasoner"}',
    '{"temperature":0.7,"maxTokens":4096,"topP":0.95,"responseFormat":"TEXT","thinkingMode":false}',
    TRUE,
    10
),
(
    'QWEN',
    '通义千问',
    '',
    'https://dashscope.aliyuncs.com/compatible-mode/v1',
    'qwen3.6-plus',
    '{"qwen3.6-plus":"qwen3.6-plus","qwen3.6-plus-2026-04-02(Qwen3.6)":"qwen3.6-plus-2026-04-02(Qwen3.6)","qwen3.5-plus":"qwen3.5-plus","qwen3.5-plus-2026-02-15(Qwen3.5)":"qwen3.5-plus-2026-02-15(Qwen3.5)","qwen3.5-flash":"qwen3.5-flash","qwen3.5-flash-2026-02-23(Qwen3.5)":"qwen3.5-flash-2026-02-23(Qwen3.5)"}',
    '{"temperature":0.7,"maxTokens":4096,"topP":0.95,"responseFormat":"TEXT","thinkingMode":false}',
    TRUE,
    20
),
(
    'KIMI',
    '月之暗面 Kimi',
    '',
    'https://api.moonshot.cn/v1',
    'kimi-k2.5',
    '{"moonshot-v1-8k":"moonshot-v1-8k","moonshot-v1-32k":"moonshot-v1-32k","moonshot-v1-128k":"moonshot-v1-128k","kimi-k2-0711-preview":"kimi-k2-0711-preview","kimi-k2-0905-preview":"kimi-k2-0905-preview","kimi-k2-turbo-preview":"kimi-k2-turbo-preview","kimi-k2-thinking":"kimi-k2-thinking","kimi-k2-thinking-turbo":"kimi-k2-thinking-turbo","kimi-k2.5":"kimi-k2.5"}',
    '{"temperature":1,"maxTokens":4096,"topP":0.95,"responseFormat":"TEXT","thinkingMode":false}',
    TRUE,
    30
),
(
    'GEMINI',
    'Google Gemini',
    '',
    'https://generativelanguage.googleapis.com/v1beta/openai/',
    'gemini-3.1-flash-lite-preview',
    '{"gemini-3.1-pro-preview":"gemini-3.1-pro-preview","gemini-3.1-pro-preview-customtools":"gemini-3.1-pro-preview-customtools","gemini-3.1-flash-lite-preview":"gemini-3.1-flash-lite-preview","gemini-3-pro-image-preview":"gemini-3-pro-image-preview","gemini-3-flash-preview":"gemini-3-flash-preview","gemini-2.5-pro":"gemini-2.5-pro","gemini-2.5-flash":"gemini-2.5-flash","gemini-2.5-flash-lite":"gemini-2.5-flash-lite","gemini-2.5-flash-image":"gemini-2.5-flash-image","gemini-flash-latest":"gemini-flash-latest","gemini-flash-lite-latest":"gemini-flash-lite-latest"}',
    '{"temperature":0.7,"maxTokens":8192,"topP":0.95,"responseFormat":"TEXT","thinkingMode":false}',
    TRUE,
    40
),
(
    'QIANFAN',
    '百度千帆',
    '',
    'https://qianfan.baidubce.com/v2',
    'ernie-4.0-turbo-128k',
    '{"ernie-3.5-128k":"ernie-3.5-128k","ernie-4.0-turbo-128k":"ernie-4.0-turbo-128k","ernie-4.5-turbo-128k":"ernie-4.5-turbo-128k","ernie-4.5-turbo-32k":"ernie-4.5-turbo-32k","ernie-lite-pro-128k":"ernie-lite-pro-128k","ernie-speed-pro-128k":"ernie-speed-pro-128k","ernie-x1-turbo-32k":"ernie-x1-turbo-32k"}',
    '{"temperature":0.7,"maxTokens":4096,"topP":0.95,"responseFormat":"TEXT","thinkingMode":false}',
    TRUE,
    50
),
(
    'MINIMAX',
    'MiniMax',
    '',
    'https://api.minimax.io/v1',
    'MiniMax-M2.7',
    '{"Abab6.5t-Chat":"Abab6.5t-Chat","Abab7-chat-preview":"Abab7-chat-preview","MiniMax-M1":"MiniMax-M1","MiniMax-M2-Her":"MiniMax-M2-Her","MiniMax-M2.1-Lightning":"MiniMax-M2.1-Lightning","MiniMax-M2.1":"MiniMax-M2.1","MiniMax-M2.5-Lightning":"MiniMax-M2.5-Lightning","MiniMax-M2.5":"MiniMax-M2.5","MiniMax-M2.7-highspeed":"MiniMax-M2.7-highspeed","MiniMax-M2.7":"MiniMax-M2.7","MiniMax-M2":"MiniMax-M2","Minimax-Text-01":"Minimax-Text-01"}',
    '{"temperature":0.7,"maxTokens":4096,"topP":0.95,"responseFormat":"TEXT","thinkingMode":false}',
    TRUE,
    60
)
ON CONFLICT (code) DO NOTHING;

-- 提交事务
COMMIT;

-- =============================================================================
-- 第三部分：配置说明
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
--
-- =============================================================================
