-- =============================================================================
-- 建库脚本（在连接到默认库 postgres 上执行）
-- 示例：psql -U postgres -h localhost -f db/00-create-databases.sql
-- 若库已存在会报错，可先 DROP DATABASE（注意无连接占用）或忽略该条错误。
-- =============================================================================

CREATE DATABASE eaju_ai
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    CONNECTION LIMIT = -1;

CREATE DATABASE eaju_ai_test
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    CONNECTION LIMIT = -1;
