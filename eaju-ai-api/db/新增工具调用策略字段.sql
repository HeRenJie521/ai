-- 为 ai_app_tool 表新增 call_strategy 字段，用于存储每个工具的调用策略
-- 调用策略指导 LLM 何时调用该工具，例如："当用户询问请假余额时调用此工具"

ALTER TABLE ai_app_tool 
ADD COLUMN call_strategy TEXT NULL;

COMMENT ON COLUMN ai_app_tool.call_strategy IS '工具调用策略，指导 LLM 何时调用该工具';

-- 查看新增的字段
-- SELECT column_name, data_type, column_comment 
-- FROM information_schema.columns 
-- WHERE table_name = 'ai_app_tool' AND column_name = 'call_strategy';
