-- ============================================================
-- V4: 统一嵌入凭证存储方式
--     去除独立 embed_token 列，嵌入集成凭证与 API Key 一致，
--     存储在 secret_hash（SHA-256）和 secret_prefix 字段中。
-- ============================================================

ALTER TABLE api_key DROP COLUMN IF EXISTS embed_token;
