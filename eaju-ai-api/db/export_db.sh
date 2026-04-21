#!/bin/bash
# PostgreSQL 导出数据库结构和数据脚本
# 用法：./export_db.sh

# 配置数据库连接信息（请根据实际情况修改）
DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="eaju_ai"
DB_USER="postgres"

# 导出目录
EXPORT_DIR="./db/export"
mkdir -p "$EXPORT_DIR"

# 导出日期
DATE=$(date +%Y%m%d_%H%M%S)

echo "开始导出数据库：$DB_NAME"
echo "导出目录：$EXPORT_DIR"

# 方式 1：使用 pg_dump 导出完整 SQL（包含结构和数据）
echo "正在导出完整数据库..."
PGPASSWORD="$DB_PASSWORD" pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
    --schema-only \
    --no-owner \
    --no-privileges \
    > "$EXPORT_DIR/schema_only.sql"

PGPASSWORD="$DB_PASSWORD" pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
    --data-only \
    --no-owner \
    --no-privileges \
    > "$EXPORT_DIR/data_only.sql"

PGPASSWORD="$DB_PASSWORD" pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
    --no-owner \
    --no-privileges \
    > "$EXPORT_DIR/full_backup_$DATE.sql"

# 方式 2：按表分别导出（便于查看和修改）
echo "正在按表导出结构和数据..."

# 获取所有表名
TABLES=$(PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -t -c \
    "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename;")

for TABLE in $TABLES; do
    echo "导出表：$TABLE"
    
    # 导出结构
    PGPASSWORD="$DB_PASSWORD" pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
        --schema-only --table="$TABLE" \
        --no-owner --no-privileges \
        > "$EXPORT_DIR/${TABLE}_schema.sql"
    
    # 导出数据
    PGPASSWORD="$DB_PASSWORD" pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
        --data-only --table="$TABLE" \
        --no-owner --no-privileges \
        > "$EXPORT_DIR/${TABLE}_data.sql"
done

echo ""
echo "导出完成！"
echo "完整备份：$EXPORT_DIR/full_backup_$DATE.sql"
echo ""
echo "文件列表："
ls -lh "$EXPORT_DIR"
