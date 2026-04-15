# SQL 索引错误修复

## ❌ 问题描述

执行 SQL 时报错：
```
Error executing DDL "create index idx_llm_provider_config_enabled_sort on llm_provider_config (enabled, sort_order)" via JDBC Statement
Error executing DDL "create index idx_chat_turn_created_at on chat_turn (created_at)" via JDBC Statement
```

## 🔍 问题原因

1. **索引已存在**：数据库中已经创建了这些索引
2. **重复创建**：SQL 文件中有重复的索引创建语句
3. **未使用 IF NOT EXISTS**：部分 CREATE INDEX 语句缺少 `IF NOT EXISTS`

## ✅ 修复方案

### 已修复的内容

1. **所有 CREATE INDEX 语句都添加了 `IF NOT EXISTS`**
2. **删除了重复的索引创建语句**

### 修复后的索引创建（10 个）

```sql
-- api_key 表
CREATE UNIQUE INDEX IF NOT EXISTS uq_api_key_secret_hash ON api_key (secret_hash);

-- llm_provider_config 表
CREATE INDEX IF NOT EXISTS idx_llm_provider_config_enabled_sort ON llm_provider_config (enabled, sort_order);

-- chat_turn 表（4 个索引）
CREATE INDEX IF NOT EXISTS idx_chat_turn_session_id ON chat_turn (session_id);
CREATE INDEX IF NOT EXISTS idx_chat_turn_created_at ON chat_turn (created_at);
CREATE INDEX IF NOT EXISTS idx_chat_turn_api_key_id ON chat_turn (api_key_id);
CREATE INDEX IF NOT EXISTS idx_chat_turn_integration_id ON chat_turn (integration_id);

-- chat_conversation 表（5 个索引）
CREATE INDEX IF NOT EXISTS idx_chat_conversation_user_last
    ON chat_conversation (user_id, last_message_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_conversation_api_key_id ON chat_conversation (api_key_id);
CREATE INDEX IF NOT EXISTS idx_chat_conversation_integration_id ON chat_conversation (integration_id);
CREATE INDEX IF NOT EXISTS idx_chat_conversation_deleted_at ON chat_conversation (deleted_at);
```

## 🚀 部署步骤

### 全新部署

```bash
# 1. 创建数据库
psql -U postgres -h 数据库主机 -c "CREATE DATABASE eaju_ai WITH OWNER = postgres ENCODING = 'UTF8';"

# 2. 执行部署脚本
psql -U postgres -h 数据库主机 -d eaju_ai -f db/deploy.sql
```

### 已有数据库

如果数据库已存在但有索引错误：

```bash
# 方式一：直接执行（IF NOT EXISTS 会跳过已存在的索引）
psql -U postgres -h 数据库主机 -d eaju_ai -f db/deploy.sql

# 方式二：删除旧索引后执行
psql -U postgres -h 数据库主机 -d eaju_ai << EOF
DROP INDEX IF EXISTS idx_llm_provider_config_enabled_sort;
DROP INDEX IF EXISTS idx_chat_turn_session_id;
DROP INDEX IF EXISTS idx_chat_turn_created_at;
DROP INDEX IF EXISTS idx_chat_turn_api_key_id;
DROP INDEX IF EXISTS idx_chat_turn_integration_id;
DROP INDEX IF EXISTS idx_chat_conversation_user_last;
DROP INDEX IF EXISTS idx_chat_conversation_api_key_id;
DROP INDEX IF EXISTS idx_chat_conversation_integration_id;
DROP INDEX IF EXISTS idx_chat_conversation_deleted_at;
EOF

# 然后执行部署脚本
psql -U postgres -h 数据库主机 -d eaju_ai -f db/deploy.sql
```

## ✅ 验证修复

```bash
# 连接数据库
psql -U postgres -h 数据库主机 -d eaju_ai

# 查看所有索引
SELECT indexname, tablename FROM pg_indexes WHERE schemaname = 'public' ORDER BY tablename;

# 应该看到以下索引：
# api_key: uq_api_key_secret_hash
# llm_provider_config: idx_llm_provider_config_enabled_sort
# chat_turn: idx_chat_turn_session_id, idx_chat_turn_created_at, 
#            idx_chat_turn_api_key_id, idx_chat_turn_integration_id
# chat_conversation: idx_chat_conversation_user_last, 
#                    idx_chat_conversation_api_key_id,
#                    idx_chat_conversation_integration_id,
#                    idx_chat_conversation_deleted_at
```

## 📝 文件说明

| 文件 | 说明 |
|------|------|
| `db/deploy.sql` | 完整的部署脚本（已修复索引问题） |
| `db/README.md` | 部署说明文档 |

## ⚠️ 注意事项

1. **IF NOT EXISTS**：所有 CREATE INDEX 都使用了此语法，可安全重复执行
2. **删除了重复索引**：`idx_llm_provider_config_enabled_sort` 只创建一次
3. **兼容旧数据库**：如果索引已存在，会自动跳过

## 🎉 修复完成

现在可以正常执行部署脚本，不会再报索引错误！

```bash
java -jar target/eaju-ai-api-1.0.0-SNAPSHOT.jar --spring.profiles.active=test
```
