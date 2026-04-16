-- 接口管理表
CREATE TABLE IF NOT EXISTS api_definition (
  id BIGSERIAL PRIMARY KEY,
  system_name VARCHAR(256) NOT NULL,
  request_url TEXT NOT NULL,
  http_method VARCHAR(16) NOT NULL DEFAULT 'POST',
  content_type VARCHAR(128) NOT NULL DEFAULT 'application/json',
  remark TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_api_definition_system_name ON api_definition(system_name);

-- 为 ai_tool 表添加 content_type 字段
ALTER TABLE ai_tool
ADD COLUMN IF NOT EXISTS content_type VARCHAR(128) DEFAULT 'application/json';
