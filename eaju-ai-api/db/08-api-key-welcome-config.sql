-- 为 api_key 表添加开场引导配置字段
-- 仅对 type=2 (WEB_EMBED) 生效
-- 注意：如果之前已执行过 JSONB 版本的脚本，需要先修改字段类型

-- 新增字段（如果不存在）
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'api_key' AND column_name = 'welcome_text') THEN
        ALTER TABLE api_key ADD COLUMN welcome_text TEXT;
    END IF;
    
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'api_key' AND column_name = 'suggestions') THEN
        ALTER TABLE api_key ADD COLUMN suggestions TEXT;
    ELSE
        -- 如果已存在但是 JSONB 类型，转换为 TEXT
        ALTER TABLE api_key ALTER COLUMN suggestions TYPE TEXT;
    END IF;
END $$;

-- 添加注释
COMMENT ON COLUMN api_key.welcome_text IS '开场白文本（WEB_EMBED 类型）';
COMMENT ON COLUMN api_key.suggestions IS '推荐问题 JSON 字符串（WEB_EMBED 类型），例如：["问题 1", "问题 2"]';
