-- =============================================================================
-- 智蚁 AI API - v8 模型管理重构迁移脚本
-- 版本：v8.0（模型管理独立化）
-- 数据库：PostgreSQL 12+
-- 执行方式：psql -U postgres -h 主机地址 -d 数据库名 -f migrate_v8.sql
-- =============================================================================
-- 改动内容：
-- 1. 新增 llm_model 表，将 modes_json 中的模型提升为独立数据行
-- 2. llm_provider_config 新增提供方行为标志，删除 default_mode/modes_json/inference_defaults_json
-- 3. ai_app 新增 llm_model_id 外键，替换 model_id + model_provider_id
-- 4. chat_conversation / chat_turn 新增 llm_model_id 外键
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 步骤 1：llm_provider_config 增加提供方行为标志列
--         这些列替换了之前硬编码的提供方特性判断逻辑
-- -----------------------------------------------------------------------------
ALTER TABLE llm_provider_config
    ADD COLUMN IF NOT EXISTS force_temperature     NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS thinking_param_style  VARCHAR(32) NOT NULL DEFAULT 'openai',
    ADD COLUMN IF NOT EXISTS json_mode_system_hint BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS strip_tool_call_index BOOLEAN     NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN llm_provider_config.force_temperature     IS '非空时强制覆盖请求中的 temperature（如 Kimi 只接受 1.0）';
COMMENT ON COLUMN llm_provider_config.thinking_param_style  IS 'thinking 参数风格：openai（thinking.type=...）或 dashscope（enable_thinking=true/false）';
COMMENT ON COLUMN llm_provider_config.json_mode_system_hint IS '使用 JSON 模式时是否自动在 system message 中注入 "json" 关键词（Qwen 等厂商要求）';
COMMENT ON COLUMN llm_provider_config.strip_tool_call_index IS '将历史 tool_calls 回传给模型前是否剥掉 index 字段（目前已全局剥掉，此列保留作文档标记）';

-- 根据 base_url 自动设置 DashScope/百炼风格
UPDATE llm_provider_config
SET thinking_param_style = 'dashscope',
    json_mode_system_hint = TRUE
WHERE base_url ILIKE '%dashscope%'
   OR base_url ILIKE '%bailian%'
   OR (base_url ILIKE '%aliyuncs.com%' AND base_url ILIKE '%compatible-mode%');

-- Kimi 强制 temperature=1
UPDATE llm_provider_config
SET force_temperature = 1.0
WHERE code = 'KIMI';

-- -----------------------------------------------------------------------------
-- 步骤 2：创建 llm_model 表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS llm_model (
    id                    BIGSERIAL    PRIMARY KEY,
    provider_id           BIGINT       NOT NULL REFERENCES llm_provider_config(id),
    name                  VARCHAR(256) NOT NULL,
    upstream_model_id     VARCHAR(256) NOT NULL,
    -- 能力标志
    text_generation       BOOLEAN      NOT NULL DEFAULT TRUE,
    deep_thinking         BOOLEAN      NOT NULL DEFAULT FALSE,
    vision                BOOLEAN      NOT NULL DEFAULT FALSE,
    stream_output         BOOLEAN      NOT NULL DEFAULT TRUE,
    tool_call             BOOLEAN      NOT NULL DEFAULT TRUE,
    force_thinking_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    -- 每模型推理默认参数
    temperature           NUMERIC(5,2),
    max_tokens            INTEGER,
    top_p                 NUMERIC(5,2),
    top_k                 INTEGER,
    frequency_penalty     NUMERIC(5,2),
    presence_penalty      NUMERIC(5,2),
    response_format       VARCHAR(64),
    thinking_mode         BOOLEAN,
    supports_thinking_api BOOLEAN      NOT NULL DEFAULT FALSE,
    context_window        INTEGER,
    -- 元信息
    sort_order            INTEGER      NOT NULL DEFAULT 0,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_llm_model_provider_id    ON llm_model (provider_id);
CREATE INDEX IF NOT EXISTS idx_llm_model_enabled_sort   ON llm_model (enabled, sort_order);

COMMENT ON TABLE llm_model                             IS '大模型实例配置（原 modes_json 中每个 mode 的结构化行）';
COMMENT ON COLUMN llm_model.name                       IS '逻辑名（即 /chat 请求体 mode 参数值，也是前端展示名称）';
COMMENT ON COLUMN llm_model.upstream_model_id          IS '实际发往上游 API 的 model 字段值';
COMMENT ON COLUMN llm_model.force_thinking_enabled     IS '是否强制开启 thinking（如 MiniMax 系列）';
COMMENT ON COLUMN llm_model.supports_thinking_api      IS '该模型是否支持 thinking API 调用（如 DeepSeek-Reasoner、Qwen 思考模型）';
COMMENT ON COLUMN llm_model.response_format            IS 'TEXT 或 JSON_OBJECT';

-- -----------------------------------------------------------------------------
-- 步骤 3：从 modes_json 迁移数据到 llm_model
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    prov        RECORD;
    mode_key    TEXT;
    mode_val    JSONB;
    up_id       TEXT;
    txt_gen     BOOLEAN;
    deep_think  BOOLEAN;
    vis_cap     BOOLEAN;
    ctx_win     INTEGER;
    sort_idx    INTEGER;
    temp_val    NUMERIC;
    max_tok_val INTEGER;
    top_p_val   NUMERIC;
    resp_fmt    TEXT;
    think_mode  BOOLEAN;
    sup_think   BOOLEAN;
    inf_json    JSONB;
BEGIN
    FOR prov IN SELECT * FROM llm_provider_config ORDER BY sort_order, id LOOP
        sort_idx := 0;
        temp_val    := NULL;
        max_tok_val := NULL;
        top_p_val   := NULL;
        resp_fmt    := NULL;
        think_mode  := NULL;
        sup_think   := FALSE;

        -- 解析 inference_defaults_json
        IF prov.inference_defaults_json IS NOT NULL AND trim(prov.inference_defaults_json) != '' THEN
            BEGIN
                inf_json    := prov.inference_defaults_json::JSONB;
                temp_val    := (inf_json->>'temperature')::NUMERIC;
                max_tok_val := (inf_json->>'maxTokens')::INTEGER;
                top_p_val   := (inf_json->>'topP')::NUMERIC;
                resp_fmt    := inf_json->>'responseFormat';
                IF inf_json ? 'thinkingMode' THEN
                    think_mode := (inf_json->>'thinkingMode')::BOOLEAN;
                END IF;
                IF inf_json ? 'supportsThinkingApi' THEN
                    sup_think := COALESCE((inf_json->>'supportsThinkingApi')::BOOLEAN, FALSE);
                END IF;
            EXCEPTION WHEN OTHERS THEN
                -- 忽略解析失败
            END;
        END IF;

        -- 解析 modes_json
        IF prov.modes_json IS NOT NULL AND trim(prov.modes_json) != '' THEN
            BEGIN
                FOR mode_key, mode_val IN SELECT key, value FROM jsonb_each(prov.modes_json::JSONB) LOOP
                    IF mode_key IS NULL OR trim(mode_key) = '' THEN
                        CONTINUE;
                    END IF;

                    IF jsonb_typeof(mode_val) = 'string' THEN
                        -- 旧格式：值即上游 model id
                        up_id      := mode_val #>> '{}';
                        txt_gen    := TRUE;
                        deep_think := FALSE;
                        vis_cap    := FALSE;
                        ctx_win    := NULL;
                    ELSIF jsonb_typeof(mode_val) = 'object' THEN
                        -- 新对象格式
                        up_id      := COALESCE(mode_val->>'upstreamModel', mode_val->>'model', mode_key);
                        txt_gen    := COALESCE((mode_val->>'textGeneration')::BOOLEAN, TRUE);
                        deep_think := COALESCE((mode_val->>'deepThinking')::BOOLEAN, FALSE);
                        vis_cap    := COALESCE((mode_val->>'vision')::BOOLEAN, FALSE);
                        ctx_win    := NULL;
                        IF mode_val ? 'contextWindow' AND jsonb_typeof(mode_val->'contextWindow') = 'number' THEN
                            ctx_win := (mode_val->>'contextWindow')::INTEGER;
                        END IF;
                    ELSE
                        CONTINUE;
                    END IF;

                    -- 剥掉版本注释，如 "model-name(v1.0)" -> "model-name"
                    up_id := trim(regexp_replace(up_id, '\s*\([^)]*\)\s*$', ''));
                    IF up_id = '' THEN
                        up_id := mode_key;
                    END IF;

                    INSERT INTO llm_model (
                        provider_id, name, upstream_model_id,
                        text_generation, deep_thinking, vision,
                        stream_output, tool_call, force_thinking_enabled,
                        temperature, max_tokens, top_p,
                        response_format, thinking_mode, supports_thinking_api,
                        context_window, sort_order, enabled
                    ) VALUES (
                        prov.id, mode_key, up_id,
                        txt_gen, deep_think, vis_cap,
                        TRUE, TRUE, FALSE,
                        temp_val, max_tok_val, top_p_val,
                        resp_fmt, think_mode, sup_think,
                        ctx_win, sort_idx * 10, TRUE
                    )
                    ON CONFLICT DO NOTHING;

                    sort_idx := sort_idx + 1;
                END LOOP;
            EXCEPTION WHEN OTHERS THEN
                RAISE NOTICE '解析 provider % modes_json 失败: %', prov.code, SQLERRM;
            END;
        END IF;
    END LOOP;
END $$;

-- 根据模型名推断 supports_thinking_api（含 reasoner/think 关键词）
UPDATE llm_model m
SET supports_thinking_api = TRUE
FROM llm_provider_config p
WHERE m.provider_id = p.id
  AND (
      p.base_url ILIKE '%deepseek%'
   OR p.base_url ILIKE '%dashscope%'
   OR p.base_url ILIKE '%bailian%'
   OR (p.base_url ILIKE '%aliyuncs.com%' AND p.base_url ILIKE '%compatible-mode%')
  );

-- 根据模型名推断 deep_thinking（含 reasoner/thinking/think 关键词）
UPDATE llm_model
SET deep_thinking = TRUE
WHERE name           ILIKE '%reason%'
   OR name           ILIKE '%think%'
   OR upstream_model_id ILIKE '%reason%'
   OR upstream_model_id ILIKE '%think%';

-- 标记 MiniMax 系列模型的 force_thinking_enabled
UPDATE llm_model
SET force_thinking_enabled = TRUE
WHERE name           ILIKE '%minimax%'
   OR upstream_model_id ILIKE '%minimax%'
   OR name           ILIKE '%abab%'
   OR upstream_model_id ILIKE '%abab%'
   OR name           ILIKE '%minimax-m%'
   OR upstream_model_id ILIKE '%minimax-m%';

-- -----------------------------------------------------------------------------
-- 步骤 4：ai_app 增加 llm_model_id 列，并迁移现有数据
-- -----------------------------------------------------------------------------
ALTER TABLE ai_app ADD COLUMN IF NOT EXISTS llm_model_id BIGINT REFERENCES llm_model(id);
COMMENT ON COLUMN ai_app.llm_model_id IS '应用绑定的模型 ID，关联 llm_model.id';

-- 迁移现有 app 的模型关联
UPDATE ai_app a
SET llm_model_id = (
    SELECT m.id
    FROM llm_model m
    WHERE m.provider_id = a.model_provider_id
      AND m.name = a.model_id
    ORDER BY m.sort_order, m.id
    LIMIT 1
)
WHERE a.model_provider_id IS NOT NULL
  AND a.model_id IS NOT NULL
  AND a.deleted = FALSE
  AND a.llm_model_id IS NULL;

-- -----------------------------------------------------------------------------
-- 步骤 5：chat_conversation / chat_turn 增加 llm_model_id 列，并迁移历史数据
-- -----------------------------------------------------------------------------
ALTER TABLE chat_conversation ADD COLUMN IF NOT EXISTS llm_model_id BIGINT REFERENCES llm_model(id);
COMMENT ON COLUMN chat_conversation.llm_model_id IS '会话最后使用的模型 ID，关联 llm_model.id';

ALTER TABLE chat_turn ADD COLUMN IF NOT EXISTS llm_model_id BIGINT REFERENCES llm_model(id);
COMMENT ON COLUMN chat_turn.llm_model_id IS '本轮使用的模型 ID，关联 llm_model.id';

-- 迁移 chat_conversation 历史数据
UPDATE chat_conversation cc
SET llm_model_id = (
    SELECT m.id
    FROM llm_model m
    JOIN llm_provider_config p ON p.id = m.provider_id
    WHERE upper(p.code) = upper(cc.last_provider_code)
      AND m.name = cc.last_mode_key
    ORDER BY m.sort_order, m.id
    LIMIT 1
)
WHERE cc.last_provider_code IS NOT NULL
  AND cc.last_mode_key IS NOT NULL
  AND cc.llm_model_id IS NULL;

-- 迁移 chat_turn 历史数据
UPDATE chat_turn ct
SET llm_model_id = (
    SELECT m.id
    FROM llm_model m
    JOIN llm_provider_config p ON p.id = m.provider_id
    WHERE upper(p.code) = upper(ct.provider)
      AND (m.name = ct.model OR m.upstream_model_id = ct.model)
    ORDER BY m.sort_order, m.id
    LIMIT 1
)
WHERE ct.provider IS NOT NULL
  AND ct.llm_model_id IS NULL;

-- -----------------------------------------------------------------------------
-- 步骤 6：删除 llm_provider_config 中的旧列
-- -----------------------------------------------------------------------------
ALTER TABLE llm_provider_config
    DROP COLUMN IF EXISTS default_mode,
    DROP COLUMN IF EXISTS modes_json,
    DROP COLUMN IF EXISTS inference_defaults_json;

-- -----------------------------------------------------------------------------
-- 步骤 7：删除 ai_app 中的旧列（在迁移完成后）
-- -----------------------------------------------------------------------------
ALTER TABLE ai_app
    DROP COLUMN IF EXISTS model_id,
    DROP COLUMN IF EXISTS model_provider_id;

COMMIT;

-- =============================================================================
-- 验证语句（可选，执行后检查结果）
-- =============================================================================
-- SELECT count(*) FROM llm_model;
-- SELECT p.display_name, m.name, m.upstream_model_id, m.deep_thinking, m.tool_call
--   FROM llm_model m JOIN llm_provider_config p ON p.id = m.provider_id
--   ORDER BY p.sort_order, m.sort_order;
-- =============================================================================
