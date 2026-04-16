-- 为 api_definition 表添加 http_method 字段
ALTER TABLE api_definition
ADD COLUMN IF NOT EXISTS http_method VARCHAR(16) NOT NULL DEFAULT 'POST';
