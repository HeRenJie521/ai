-- =====================================================
-- 系统管理员账号表 - 数据库迁移脚本
-- 创建时间：2026-04-21
-- 说明：将管理员配置从配置文件迁移到数据库
-- =====================================================

-- 创建系统管理员账号表
CREATE TABLE IF NOT EXISTS admin_account (
    id BIGSERIAL PRIMARY KEY,
    phone VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_admin_account_enabled ON admin_account(enabled);
CREATE INDEX idx_admin_account_phone ON admin_account(phone);

-- 添加注释
COMMENT ON TABLE admin_account IS '系统管理员账号表';
COMMENT ON COLUMN admin_account.id IS '主键 ID';
COMMENT ON COLUMN admin_account.phone IS '管理员手机号';
COMMENT ON COLUMN admin_account.name IS '姓名';
COMMENT ON COLUMN admin_account.enabled IS '是否启用';
COMMENT ON COLUMN admin_account.created_at IS '创建时间';
COMMENT ON COLUMN admin_account.updated_at IS '更新时间';

-- =====================================================
-- 数据迁移：将原配置文件中的管理员手机号迁移到数据库
-- 原始配置：15296711325,13403928938,15101120754,18001077289
-- =====================================================

-- 插入默认管理员账号（如果不存在）
INSERT INTO admin_account (phone, name, enabled, created_at, updated_at)
SELECT '15296711325', '何仁杰', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM admin_account WHERE phone = '15296711325');

INSERT INTO admin_account (phone, name, enabled, created_at, updated_at)
SELECT '13403928938', '沐云飞', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM admin_account WHERE phone = '13403928938');

INSERT INTO admin_account (phone, name, enabled, created_at, updated_at)
SELECT '15101120754', '吴洪生', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM admin_account WHERE phone = '15101120754');

INSERT INTO admin_account (phone, name, enabled, created_at, updated_at)
SELECT '18001077289', '洪胜高', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM admin_account WHERE phone = '18001077289');

-- =====================================================
-- 完成提示
-- =====================================================
-- 迁移完成后：
-- 1. 管理员登录判断逻辑已改为查询 admin_account 表
-- 2. 原配置文件 app.auth.admin-phones 已移除
-- 3. 可通过系统设置 -> 系统管理员 菜单管理账号
-- =====================================================
