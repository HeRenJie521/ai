-- =============================================================================
-- 大模型提供方配置表 llm_provider_config（与 JPA 实体 LlmProviderConfigEntity 一致）
--
-- 原 classpath:config/*.yml 中的 ai.* 配置已迁至此表；YAML 文件可保留作参考，应用不再 import。
-- api_key 种子为空字符串，请在库中更新或通过后续管理接口写入，勿把密钥提交进仓库。
--
-- 执行示例（在已建业务库上）：
--   psql -U postgres -h localhost -d eaju_ai -f db/02-llm-provider-config.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS llm_provider_config (
    id                         BIGSERIAL PRIMARY KEY,
    -- 对外请求体 provider 字符串，建议大写，如 DEEPSEEK、QWEN；大小写不敏感查询
    code                       VARCHAR(64)  NOT NULL UNIQUE,
    display_name               VARCHAR(128) NOT NULL,
    api_key                    TEXT         NOT NULL DEFAULT '',
    base_url                   TEXT         NOT NULL,
    default_mode               VARCHAR(256) NOT NULL,
    -- modes：JSON 对象，键为逻辑 mode，值为上游 model id
    modes_json                 TEXT         NOT NULL,
    -- inference-defaults：JSON 对象，字段名用 camelCase（temperature、maxTokens、topP…）
    inference_defaults_json    TEXT,
    enabled                    BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order                 INTEGER      NOT NULL DEFAULT 0,
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_llm_provider_config_enabled_sort
    ON llm_provider_config (enabled, sort_order);

COMMENT ON TABLE llm_provider_config IS 'OpenAI 兼容 Chat Completions 的提供方配置（替代各品牌 yaml）';
COMMENT ON COLUMN llm_provider_config.code IS '与 POST /chat 请求体 provider 对应';
COMMENT ON COLUMN llm_provider_config.modes_json IS '逻辑 mode -> 上游 model id，JSON 对象';
COMMENT ON COLUMN llm_provider_config.inference_defaults_json IS '默认推理参数 JSON，可为空表示全用请求覆盖';

-- 与 chat_turn.provider 长度对齐，便于存任意已配置 code
ALTER TABLE chat_turn ALTER COLUMN provider TYPE VARCHAR(64);

-- 种子数据：首次插入；已存在 code 时不覆盖（避免覆盖你已写入的 api_key）
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
