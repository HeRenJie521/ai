-- =============================================================================
-- 对话落库表 chat_turn（与 JPA 实体 com.eaju.ai.persistence.entity.ChatTurnEntity 一致）
--
-- 请在「目标业务库」上各执行一次，例如：
--   psql -U postgres -h localhost -d eaju_ai      -f db/01-chat-turn-table.sql
--   psql -U postgres -h localhost -d eaju_ai_test -f db/01-chat-turn-table.sql
--
-- 正式环境：将 -d 换成你的生产库名后再执行同一脚本即可。
-- =============================================================================

CREATE TABLE IF NOT EXISTS chat_turn (
    -- 主键：自增唯一标识本条记录
    id                      BIGSERIAL PRIMARY KEY,
    -- 会话 ID：多轮对话分组，与 Redis 键 chat:session:{sessionId} 对应；无多轮时可为空
    session_id              VARCHAR(128),
    -- 业务用户 ID：可选，用于与你们用户体系关联
    user_id                 VARCHAR(128),
    -- 大模型提供方：与 POST /chat 的 provider 字符串一致（与 llm_provider_config.code 对应）
    provider                VARCHAR(64)  NOT NULL,
    -- 本次调用实际使用的上游模型名（解析 mode 后的 model id）
    model                   VARCHAR(256),
    -- 本回合客户端在请求体里传入的 messages 列表的 JSON（不含 Redis 里拼上的历史）
    client_messages_json    TEXT,
    -- 实际发给大模型的完整 messages 的 JSON（含 Redis 会话历史 + 本轮用户消息）
    request_messages_json   TEXT,
    -- 助手回复正文：模型返回的可见回答内容
    assistant_content       TEXT,
    -- 思维链 / 推理内容：如 DeepSeek 思考模式下的 reasoning_content；无则为空
    reasoning_content       TEXT,
    -- 上游返回的消息 ID：如 OpenAI 兼容协议里的 id，便于对账与排查
    upstream_message_id     VARCHAR(128),
    -- 结束原因：如 stop、length 等，对应上游 choices[0].finish_reason
    finish_reason           VARCHAR(64),
    -- 提示侧 token 数：本次请求消耗的 prompt tokens（来自 usage.prompt_tokens）
    prompt_tokens           INTEGER,
    -- 生成侧 token 数：本次回复消耗的 completion tokens（来自 usage.completion_tokens）
    completion_tokens       INTEGER,
    -- 总 token 数：prompt + completion 等合计（来自 usage.total_tokens）
    total_tokens            INTEGER,
    -- 是否流式调用：true 表示请求曾以流式发起（当前流式路径若未落库则多为 false）
    stream_mode             BOOLEAN      NOT NULL DEFAULT FALSE,
    -- 记录创建时间：本条落库时间（UTC 存库，TIMESTAMPTZ）
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_turn_session_id
    ON chat_turn (session_id);

CREATE INDEX IF NOT EXISTS idx_chat_turn_created_at
    ON chat_turn (created_at);

-- 表级说明（PostgreSQL 注释，便于客户端与文档工具展示）
COMMENT ON TABLE chat_turn IS '单次对话轮次：记录用户侧消息、完整请求上下文、助手回复、token 与上游元数据';

COMMENT ON COLUMN chat_turn.id IS '主键：自增唯一标识';
COMMENT ON COLUMN chat_turn.session_id IS '会话 ID：多轮对话分组，对应 Redis 会话；无会话则为空';
COMMENT ON COLUMN chat_turn.user_id IS '业务用户 ID：可选，关联业务账号';
COMMENT ON COLUMN chat_turn.provider IS '大模型提供方：DEEPSEEK、QWEN、KIMI、GEMINI、QIANFAN、MINIMAX 等';
COMMENT ON COLUMN chat_turn.model IS '上游模型名：本次调用实际使用的 model 字符串';
COMMENT ON COLUMN chat_turn.client_messages_json IS '客户端本轮 messages 的 JSON：不含 Redis 历史';
COMMENT ON COLUMN chat_turn.request_messages_json IS '发给模型的完整 messages 的 JSON：含 Redis 历史与本轮用户消息';
COMMENT ON COLUMN chat_turn.assistant_content IS '助手回复正文：模型对用户可见的回答';
COMMENT ON COLUMN chat_turn.reasoning_content IS '推理/思维链内容：如思考模式返回的 reasoning_content';
COMMENT ON COLUMN chat_turn.upstream_message_id IS '上游消息 ID：OpenAI 兼容响应中的 id';
COMMENT ON COLUMN chat_turn.finish_reason IS '结束原因：如 stop、length，对应 finish_reason';
COMMENT ON COLUMN chat_turn.prompt_tokens IS '提示 token 数：usage 中的 prompt_tokens';
COMMENT ON COLUMN chat_turn.completion_tokens IS '生成 token 数：usage 中的 completion_tokens';
COMMENT ON COLUMN chat_turn.total_tokens IS '总 token 数：usage 中的 total_tokens';
COMMENT ON COLUMN chat_turn.stream_mode IS '是否流式请求：流式接口为 true（若实现落库）；阻塞为 false';
COMMENT ON COLUMN chat_turn.created_at IS '记录创建时间：写入数据库的时间（带时区）';
