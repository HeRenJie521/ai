-- PostgreSQL 删除所有表（带 CASCADE 级联删除外键约束）
-- 执行前请确认是测试环境！生产环境慎用！

-- 方式 1：删除 schema 中所有表（推荐）
DO $$ 
DECLARE 
    r RECORD; 
BEGIN 
    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP 
        EXECUTE 'DROP TABLE IF EXISTS public.' || quote_ident(r.tablename) || ' CASCADE'; 
    END LOOP; 
END $$;

-- 删除所有序列
DO $$ 
DECLARE 
    r RECORD; 
BEGIN 
    FOR r IN (SELECT sequencename FROM pg_sequences WHERE schemaname = 'public') LOOP 
        EXECUTE 'DROP SEQUENCE IF EXISTS public.' || quote_ident(r.sequencename) || ' CASCADE'; 
    END LOOP; 
END $$;

-- 删除所有视图
DO $$ 
DECLARE 
    r RECORD; 
BEGIN 
    FOR r IN (SELECT viewname FROM pg_views WHERE schemaname = 'public') LOOP 
        EXECUTE 'DROP VIEW IF EXISTS public.' || quote_ident(r.viewname) || ' CASCADE'; 
    END LOOP; 
END $$;

-- 重置所有自增序列（如果需要）
-- SELECT setval('api_definition_id_seq', 1, false);
-- SELECT setval('ai_tool_id_seq', 1, false);
-- SELECT setval('ai_app_id_seq', 1, false);
-- SELECT setval('ai_app_tool_id_seq', 1, false);
-- SELECT setval('api_key_id_seq', 1, false);
-- SELECT setval('chat_turn_id_seq', 1, false);
-- SELECT setval('llm_model_id_seq', 1, false);
-- SELECT setval('llm_provider_config_id_seq', 1, false);
-- SELECT setval('user_context_id_seq', 1, false);
-- SELECT setval('user_context_field_id_seq', 1, false);
