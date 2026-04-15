# 数据库部署说明

## 📁 文件说明

现在 `db/` 目录只有一个 SQL 文件：

```
db/
└── deploy.sql    # 完整的数据库部署脚本
```

## 🚀 部署步骤

### 1. 创建数据库

```bash
# 连接到 PostgreSQL
psql -U postgres -h 数据库主机

# 创建数据库
CREATE DATABASE eaju_ai
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    CONNECTION LIMIT = -1;

\q
```

### 2. 执行部署脚本

```bash
# 测试环境
psql -U postgres -h 10.0.254.21 -d eaju_ai -f db/deploy.sql

# 生产环境
psql -U postgres -h 生产数据库主机 -d eaju_ai -f db/deploy.sql
```

### 3. 更新 API Key

```bash
# 连接到数据库
psql -U postgres -h 数据库主机 -d eaju_ai

-- 更新各平台 API Key
UPDATE llm_provider_config SET api_key = '你的 DeepSeek 密钥' WHERE code = 'DEEPSEEK';
UPDATE llm_provider_config SET api_key = '你的通义千问密钥' WHERE code = 'QWEN';
UPDATE llm_provider_config SET api_key = '你的 Kimi 密钥' WHERE code = 'KIMI';
UPDATE llm_provider_config SET api_key = '你的 Gemini 密钥' WHERE code = 'GEMINI';
UPDATE llm_provider_config SET api_key = '你的千帆密钥' WHERE code = 'QIANFAN';
UPDATE llm_provider_config SET api_key = '你的 MiniMax 密钥' WHERE code = 'MINIMAX';

-- 验证更新
SELECT code, display_name, LEFT(api_key, 10) as api_key_preview FROM llm_provider_config;

\q
```

### 4. 验证表结构

```bash
# 连接数据库
psql -U postgres -h 数据库主机 -d eaju_ai

# 查看表
\dt

# 查看表结构
\d api_key
\d chat_turn
\d chat_conversation
\d llm_provider_config

# 应该看到以下字段：
# api_key: id, name, secret_hash, secret_prefix, enabled, deleted, 
#          created_at, updated_at, type, default_model, allowed_origins,
#          welcome_text, suggestions, system_role, system_task, system_constraints

# chat_turn: id, session_id, user_id, provider, model, client_messages_json,
#            request_messages_json, assistant_content, reasoning_content,
#            upstream_message_id, finish_reason, prompt_tokens, completion_tokens,
#            total_tokens, stream_mode, api_key_id, integration_id, created_at

# chat_conversation: id, user_id, session_id, title, last_message_at, created_at,
#                    last_provider_code, last_mode_key, api_key_id, integration_id,
#                    last_model, context_window, deleted_at

\q
```

## 📋 数据库表说明

### api_key 表
- **用途**：API Key 和嵌入网站集成管理
- **关键字段**：
  - `type`: 1=API_KEY, 2=WEB_EMBED
  - `welcome_text`, `suggestions`: 嵌入网站开场白配置
  - `system_role`, `system_task`, `system_constraints`: Agent 配置

### chat_turn 表
- **用途**：单次对话轮次记录
- **关键字段**：
  - `provider`, `model`: 模型提供方和模型
  - `client_messages_json`, `request_messages_json`: 消息 JSON
  - `assistant_content`, `reasoning_content`: 回复内容
  - `api_key_id`, `integration_id`: 集成 ID

### chat_conversation 表
- **用途**：会话列表
- **关键字段**：
  - `user_id`, `session_id`: 用户和会话标识
  - `last_message_at`: 最后消息时间
  - `deleted_at`: 逻辑删除时间

### llm_provider_config 表
- **用途**：大模型提供方配置
- **种子数据**：DeepSeek、通义千问、Kimi、Gemini、百度千帆、MiniMax

## ⚠️ 注意事项

1. **只执行一次**：`deploy.sql` 只需在初次部署时执行
2. **API Key 更新**：种子数据中的 `api_key` 为空，需要手动更新
3. **生产环境**：务必使用强密码和安全的 JWT 密钥

## 🔧 启动应用

```bash
# 测试环境
java -jar target/eaju-ai-api-1.0.0-SNAPSHOT.jar --spring.profiles.active=test

# 生产环境
java -jar target/eaju-ai-api-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod
```

启动后应用会自动检查表结构，无需手动执行任何 DDL。
