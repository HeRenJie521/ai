# SQL 文件整理完成报告

## ✅ 整理完成

### 文件变更

**之前**：14 个 SQL 文件
```
db/
├── 00-create-databases.sql
├── 01-chat-turn-table.sql
├── 02-llm-provider-config.sql
├── 03-chat-conversation.sql
├── 04-api-key.sql
├── 04-external-user-migration.sql
├── 05-kimi-temperature.sql
├── 06-chat-conversation-last-model.sql
├── 07-mode-context-window.sql
├── 08-api-key-welcome-config.sql
├── 09-api-key-system-prompt.sql
├── deploy.sql
├── deploy-full.sql
└── patch-missing-fields.sql
```

**现在**：1 个 SQL 文件
```
db/
├── deploy.sql         # 完整的数据库部署脚本
└── README.md          # 部署说明文档
```

---

## 📄 deploy.sql 包含内容

### 1. 创建表（4 个）
- ✅ `api_key` - API Key 和集成管理表（包含所有字段）
- ✅ `chat_turn` - 对话轮次表（包含所有字段）
- ✅ `chat_conversation` - 会话列表表（包含所有字段）
- ✅ `llm_provider_config` - 大模型提供方配置表

### 2. 所有字段

**api_key 表（16 个字段）：**
1. `id` - 主键
2. `name` - 名称
3. `secret_hash` - 密钥哈希
4. `secret_prefix` - 密钥前缀
5. `enabled` - 启用状态
6. `deleted` - 删除状态
7. `created_at` - 创建时间
8. `updated_at` - 更新时间
9. `type` - 类型（1=API_KEY, 2=WEB_EMBED）
10. `default_model` - 默认模型
11. `allowed_origins` - 允许的来源域名
12. `welcome_text` - 开场白文本
13. `suggestions` - 推荐问题
14. `system_role` - Agent 角色设定 ⭐
15. `system_task` - Agent 任务指令 ⭐
16. `system_constraints` - Agent 限制条件 ⭐

**chat_turn 表（18 个字段）：**
1. `id` - 主键
2. `session_id` - 会话 ID
3. `user_id` - 用户 ID
4. `provider` - 提供方
5. `model` - 模型
6. `client_messages_json` - 客户端消息 JSON
7. `request_messages_json` - 请求消息 JSON
8. `assistant_content` - 助手回复
9. `reasoning_content` - 推理内容
10. `upstream_message_id` - 上游消息 ID
11. `finish_reason` - 结束原因
12. `prompt_tokens` - 提示 token 数
13. `completion_tokens` - 生成 token 数
14. `total_tokens` - 总 token 数
15. `stream_mode` - 是否流式
16. `api_key_id` - API Key ID
17. `integration_id` - 集成 ID
18. `created_at` - 创建时间

**chat_conversation 表（14 个字段）：**
1. `id` - 主键
2. `user_id` - 用户 ID
3. `session_id` - 会话 ID
4. `title` - 标题
5. `last_message_at` - 最后消息时间
6. `created_at` - 创建时间
7. `last_provider_code` - 最后提供方
8. `last_mode_key` - 最后模式
9. `api_key_id` - API Key ID
10. `integration_id` - 集成 ID
11. `last_model` - 最后模型
12. `context_window` - 上下文窗口
13. `deleted_at` - 删除时间
14. `UNIQUE (user_id, session_id)` - 唯一约束

**llm_provider_config 表（11 个字段）：**
1. `id` - 主键
2. `code` - 代码
3. `display_name` - 显示名称
4. `api_key` - API Key
5. `base_url` - 基础 URL
6. `default_mode` - 默认模式
7. `modes_json` - 模式 JSON
8. `inference_defaults_json` - 推理默认值 JSON
9. `enabled` - 启用状态
10. `sort_order` - 排序
11. `created_at` - 创建时间
12. `updated_at` - 更新时间

### 3. 索引（11 个）
- ✅ `uq_api_key_secret_hash` - API Key 密钥唯一索引
- ✅ `idx_chat_turn_session_id` - 会话 ID 索引
- ✅ `idx_chat_turn_created_at` - 创建时间索引
- ✅ `idx_chat_turn_api_key_id` - API Key ID 索引
- ✅ `idx_chat_turn_integration_id` - 集成 ID 索引
- ✅ `idx_chat_conversation_user_last` - 用户最后消息索引
- ✅ `idx_chat_conversation_api_key_id` - API Key ID 索引
- ✅ `idx_chat_conversation_integration_id` - 集成 ID 索引
- ✅ `idx_chat_conversation_deleted_at` - 删除时间索引
- ✅ `idx_llm_provider_config_enabled_sort` - 启用排序索引

### 4. 种子数据（6 条）
- ✅ DEEPSEEK - DeepSeek 配置
- ✅ QWEN - 通义千问配置
- ✅ KIMI - 月之暗面 Kimi 配置
- ✅ GEMINI - Google Gemini 配置
- ✅ QIANFAN - 百度千帆配置
- ✅ MINIMAX - MiniMax 配置

---

## 🚀 部署方式

### 全新部署

```bash
# 1. 创建数据库
psql -U postgres -h 数据库主机 << EOF
CREATE DATABASE eaju_ai WITH OWNER = postgres ENCODING = 'UTF8';
EOF

# 2. 执行部署脚本
psql -U postgres -h 数据库主机 -d eaju_ai -f db/deploy.sql

# 3. 更新 API Key
psql -U postgres -h 数据库主机 -d eaju_ai << EOF
UPDATE llm_provider_config SET api_key = '你的密钥' WHERE code = 'DEEPSEEK';
UPDATE llm_provider_config SET api_key = '你的密钥' WHERE code = 'QWEN';
...
EOF
```

### 已有数据库

如果已有数据库，直接执行：
```bash
psql -U postgres -h 数据库主机 -d eaju_ai -f db/deploy.sql
```

`CREATE TABLE IF NOT EXISTS` 语句确保不会重复创建表。

---

## 📝 验证部署

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

# 查看种子数据
SELECT code, display_name FROM llm_provider_config ORDER BY sort_order;
```

---

## ⚠️ 重要提示

1. **deploy.sql 是唯一的 SQL 文件**，其他所有 SQL 文件已删除
2. **包含所有字段**，包括 `deleted_at`、`integration_id`、`system_role` 等
3. **使用 IF NOT EXISTS**，可安全重复执行
4. **种子数据使用 ON CONFLICT**，不会覆盖已更新的 API Key

---

## 📞 需要帮助？

请查看 `db/README.md` 获取详细部署说明。
