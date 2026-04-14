-- ============================================================
-- V2: 新增嵌入网站（Web Embed）集成类型
-- 使用 ddl-auto=update 环境可跳过此脚本（Hibernate 自动建列）
-- 生产环境（ddl-auto=validate/none）请手动执行
-- ============================================================

-- 1. api_key 表扩展集成类型相关字段
ALTER TABLE api_key
    ADD COLUMN IF NOT EXISTS type              SMALLINT     NOT NULL DEFAULT 1
        CONSTRAINT chk_api_key_type CHECK (type IN (1,2)),
    ADD COLUMN IF NOT EXISTS default_model     VARCHAR(256)          DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS allowed_origins   VARCHAR(1000)         DEFAULT NULL,
    -- embed_token 明文存储（服务端 S2S 凭证，无需在请求头中传输）
    -- 生产环境建议在数据库层做列级加密
    ADD COLUMN IF NOT EXISTS embed_token       CHAR(64)              DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS embed_token_prefix VARCHAR(24)          DEFAULT NULL;

COMMENT ON COLUMN api_key.type IS '1=API_KEY  2=WEB_EMBED';
COMMENT ON COLUMN api_key.default_model IS '嵌入网站默认对话模型 ID';
COMMENT ON COLUMN api_key.allowed_origins IS '允许的嵌入来源域名（逗号分隔，为空表示不限）';
COMMENT ON COLUMN api_key.embed_token IS '嵌入免密登录 HMAC 签名密钥（64位随机 hex）';
COMMENT ON COLUMN api_key.embed_token_prefix IS '展示用前缀（前16位）';

-- 2. chat_conversation 追加 integration_id（WEB_EMBED 场景关联集成）
ALTER TABLE chat_conversation
    ADD COLUMN IF NOT EXISTS integration_id BIGINT DEFAULT NULL;

CREATE INDEX IF NOT EXISTS idx_chat_conv_integration_id
    ON chat_conversation (integration_id);

COMMENT ON COLUMN chat_conversation.integration_id IS '嵌入网站集成ID（api_key.id），为 NULL 表示普通用户会话';

-- 3. chat_turn 追加 integration_id（用于按集成统计用量）
ALTER TABLE chat_turn
    ADD COLUMN IF NOT EXISTS integration_id BIGINT DEFAULT NULL;

CREATE INDEX IF NOT EXISTS idx_chat_turn_integration_id
    ON chat_turn (integration_id);

COMMENT ON COLUMN chat_turn.integration_id IS '嵌入网站集成ID（api_key.id），为 NULL 表示普通 JWT/API-Key 调用';
