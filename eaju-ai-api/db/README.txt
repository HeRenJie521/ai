执行顺序（PostgreSQL 已安装，本机默认 postgres / 端口 5432）：

1）建库（连到 postgres 库执行）
   psql -U postgres -h localhost -f db/00-create-databases.sql

2）建表（开发库）
   psql -U postgres -h localhost -d eaju_ai -f db/01-chat-turn-table.sql

3）大模型提供方配置表与种子（开发库；会 ALTER chat_turn.provider 为 VARCHAR(64)）
   psql -U postgres -h localhost -d eaju_ai -f db/02-llm-provider-config.sql

4）会话列表 chat_conversation（user_id 为手机号；开发库）
   psql -U postgres -h localhost -d eaju_ai -f db/03-chat-conversation.sql
   若曾执行过旧版含 app_user 的脚本，请先执行：db/04-external-user-migration.sql

5）建表（测试库，与开发库结构相同）
   psql -U postgres -h localhost -d eaju_ai_test -f db/01-chat-turn-table.sql
   psql -U postgres -h localhost -d eaju_ai_test -f db/02-llm-provider-config.sql
   psql -U postgres -h localhost -d eaju_ai_test -f db/03-chat-conversation.sql

正式库：把第 2～4 步的 -d 换成生产库名后依次执行 01、02、03 即可。

若使用 JPA ddl-auto=update，表可能已自动创建；本脚本使用 IF NOT EXISTS，重复执行一般安全。
