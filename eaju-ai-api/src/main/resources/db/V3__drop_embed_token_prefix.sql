-- ============================================================
-- V3: 移除 api_key.embed_token_prefix
--     签名机制去除后，列表直接展示完整 embed_token，前缀字段不再需要
-- ============================================================

ALTER TABLE api_key DROP COLUMN IF EXISTS embed_token_prefix;
