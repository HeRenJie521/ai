-- 为 ai_app 表新增 model_provider_id 字段，用于关联 llm_provider_config 表
-- 解决当多个供应商提供相同 modelId 时无法区分的问题，且避免 code 变更导致的问题

ALTER TABLE ai_app 
ADD COLUMN model_provider_id BIGINT NULL REFERENCES llm_provider_config(id);

COMMENT ON COLUMN ai_app.model_provider_id IS '模型供应商 ID，关联 llm_provider_config 表';
        
-- 查看新增的字段
-- SELECT column_name, data_type, column_comment 
-- FROM information_schema.columns 
-- WHERE table_name = 'ai_app' AND column_name = 'model_provider_id';
