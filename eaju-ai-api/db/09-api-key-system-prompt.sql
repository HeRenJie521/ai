-- 为 api_key 表添加结构化 Agent Prompt 字段
-- 仅对 type=2 (WEB_EMBED) 生效

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'api_key' AND column_name = 'system_role') THEN
        ALTER TABLE api_key ADD COLUMN system_role TEXT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'api_key' AND column_name = 'system_task') THEN
        ALTER TABLE api_key ADD COLUMN system_task TEXT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'api_key' AND column_name = 'system_constraints') THEN
        ALTER TABLE api_key ADD COLUMN system_constraints TEXT;
    END IF;
END $$;

COMMENT ON COLUMN api_key.system_role        IS 'Agent 角色设定（WEB_EMBED）';
COMMENT ON COLUMN api_key.system_task        IS 'Agent 任务指令（WEB_EMBED）';
COMMENT ON COLUMN api_key.system_constraints IS 'Agent 限制条件（WEB_EMBED）';
