-- migrate_v6_patch.sql
-- 如果已运行过旧版 migrate_v6.sql，执行此补丁添加新列

-- user_context_field 新增 field_type 和 parse_expression
ALTER TABLE user_context_field
    ADD COLUMN IF NOT EXISTS field_type       VARCHAR(32) NOT NULL DEFAULT 'String',
    ADD COLUMN IF NOT EXISTS parse_expression TEXT        NOT NULL DEFAULT '';

-- ai_tool 新增 content_type、method_name、data_params_json、response_params_json
ALTER TABLE ai_tool
    ADD COLUMN IF NOT EXISTS content_type         VARCHAR(128) NOT NULL DEFAULT 'application/json',
    ADD COLUMN IF NOT EXISTS method_name          VARCHAR(256),
    ADD COLUMN IF NOT EXISTS data_params_json     TEXT,
    ADD COLUMN IF NOT EXISTS response_params_json TEXT;  -- 出参字段说明 JSON（帮助 LLM 理解返回值）
