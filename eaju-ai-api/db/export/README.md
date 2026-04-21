# 数据库迁移指南

## 文件说明

### 1. 清理脚本
- `cleanup_all_tables.sql` - 删除测试环境所有表、序列、视图（**执行前请确认是测试环境！**）

### 2. 导入脚本
- `import_to_test.sql` - 完整的数据库结构和数据（推荐）
- `schema_only.sql` - 仅数据库结构
- `data_only.sql` - 仅数据库数据
- `zhiyi_full_backup.sql` - 本地数据库完整备份（含 ownership 信息）

### 3. 迁移脚本
- `migration/V20260421__add_api_definition_id_to_ai_tool.sql` - 添加 api_definition_id 外键关联的迁移脚本

## 使用步骤

### 步骤 1: 清理测试环境数据库

```bash
# 连接到测试环境数据库
psql -h <测试环境数据库地址> -U <用户名> -d <数据库名>

# 执行清理脚本（**谨慎执行！会删除所有数据！**）
\i /path/to/cleanup_all_tables.sql
```

或者在命令行直接执行：
```bash
PGPASSWORD=<密码> psql -h <host> -U <用户名> -d <数据库名> -f cleanup_all_tables.sql
```

### 步骤 2: 导入本地数据库结构和数据

```bash
# 方法 1：导入完整结构和数据（推荐）
PGPASSWORD=<密码> psql -h <host> -U <用户名> -d <数据库名> -f import_to_test.sql

# 方法 2：分别导入结构和数据
PGPASSWORD=<密码> psql -h <host> -U <用户名> -d <数据库名> -f schema_only.sql
PGPASSWORD=<密码> psql -h <host> -U <用户名> -d <数据库名> -f data_only.sql
```

### 步骤 3: 执行迁移脚本（如需要）

如果导入的是旧版数据库结构，需要执行迁移脚本添加 api_definition_id 外键：

```bash
PGPASSWORD=<密码> psql -h <host> -U <用户名> -d <数据库名> -f migration/V20260421__add_api_definition_id_to_ai_tool.sql
```

**注意**：如果导入的已经是新版结构（包含 api_definition_id 列），则无需执行此步骤。

## 本地数据库信息

- **数据库名**: zhiyi
- **主机**: localhost:5432
- **用户名**: postgres
- **密码**: root

## 测试环境数据库信息

请根据实际情况修改：
- **数据库名**: eaju_ai
- **主机**: 10.0.254.21:5432
- **用户名**: eaju_ai_user
- **密码**: anbang@123

## 验证导入

导入完成后，可以执行以下 SQL 验证：

```sql
-- 查看所有表
\dt

-- 查看 ai_tool 表结构
\d ai_tool

-- 查看 api_definition 表数据
SELECT id, system_name, request_url, http_method FROM api_definition;

-- 查看 ai_tool 表数据（应包含 api_definition_id 列）
SELECT id, name, label, api_definition_id FROM ai_tool;
```

## 注意事项

1. **执行清理脚本前务必备份测试环境数据！**
2. 导入数据会覆盖现有数据，请确保在测试环境执行
3. 导入后可能需要重置序列值：
   ```sql
   SELECT setval('ai_tool_id_seq', (SELECT MAX(id) FROM ai_tool), true);
   SELECT setval('api_definition_id_seq', (SELECT MAX(id) FROM api_definition), true);
   -- 对其他表也执行类似操作
   ```
4. 如果遇到外键约束错误，请先导入结构，再导入数据

## 导出的表列表

本次导出的表包括：
- ai_app - AI 应用配置
- ai_app_tool - AI 应用工具绑定
- ai_tool - AI 工具配置
- api_definition - 接口定义
- api_key - API Key 管理
- chat_conversation - 对话会话
- chat_turn - 对话轮次
- llm_model - LLM 模型
- llm_provider_config - LLM 提供商配置
- user_context_field - 用户上下文字段
