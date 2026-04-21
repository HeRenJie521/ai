-- PostgreSQL 迁移脚本：在 ai_tool 表中添加 api_definition_id 外键关联
-- 执行前请备份数据！

-- 步骤 1：添加 api_definition_id 列
ALTER TABLE ai_tool ADD COLUMN api_definition_id BIGINT;

-- 步骤 2：为每个不同的 (url, http_method, content_type) 组合创建 api_definition 记录（如果不存在）
-- 注意：不指定 id，让序列自动生成
INSERT INTO api_definition (system_name, request_url, http_method, content_type, remark, created_at, updated_at)
SELECT DISTINCT 
    '自动迁移_' || SUBSTRING(MD5(COALESCE(t.url, '') || COALESCE(t.http_method, 'POST') || COALESCE(t.content_type, 'application/json')), 1, 8) AS system_name,
    t.url AS request_url,
    COALESCE(t.http_method, 'POST') AS http_method,
    COALESCE(t.content_type, 'application/json') AS content_type,
    '从 ai_tool 表自动迁移' AS remark,
    CURRENT_TIMESTAMP AS created_at,
    CURRENT_TIMESTAMP AS updated_at
FROM ai_tool t
WHERE t.url IS NOT NULL 
  AND NOT EXISTS (
      SELECT 1 FROM api_definition a 
      WHERE a.request_url = t.url 
        AND a.http_method = COALESCE(t.http_method, 'POST')
        AND a.content_type = COALESCE(t.content_type, 'application/json')
  );

-- 步骤 3：更新 ai_tool 表，设置 api_definition_id 关联
UPDATE ai_tool t
SET api_definition_id = a.id
FROM api_definition a
WHERE a.request_url = t.url 
  AND a.http_method = COALESCE(t.http_method, 'POST')
  AND a.content_type = COALESCE(t.content_type, 'application/json');

-- 步骤 4：添加外键约束
ALTER TABLE ai_tool 
ADD CONSTRAINT fk_ai_tool_api_definition 
FOREIGN KEY (api_definition_id) REFERENCES api_definition(id) ON DELETE SET NULL;

-- 步骤 5：为 api_definition_id 添加索引以提升查询性能
CREATE INDEX IF NOT EXISTS idx_ai_tool_api_definition_id ON ai_tool(api_definition_id);

-- 步骤 6：修复序列值，确保下次插入时 ID 不会冲突
SELECT setval('api_definition_id_seq', COALESCE((SELECT MAX(id) FROM api_definition), 1), true);

-- 步骤 6：删除 ai_tool 表中不再需要的列
ALTER TABLE ai_tool DROP COLUMN http_method;
ALTER TABLE ai_tool DROP COLUMN url;
ALTER TABLE ai_tool DROP COLUMN content_type;
