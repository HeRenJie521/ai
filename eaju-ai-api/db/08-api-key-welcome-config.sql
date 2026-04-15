-- 为 api_key 表添加开场引导配置字段
-- 仅对 type=2 (WEB_EMBED) 生效

ALTER TABLE api_key
ADD COLUMN welcome_text TEXT,
ADD COLUMN suggestions JSONB;

COMMENT ON COLUMN api_key.welcome_text IS '开场白文本（WEB_EMBED 类型）';
COMMENT ON COLUMN api_key.suggestions IS '推荐问题 JSON 数组（WEB_EMBED 类型），例如: ["问题1", "问题2"]';
