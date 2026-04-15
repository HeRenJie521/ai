# YAML 配置修复说明

## ❌ 问题描述

YAML 报错：`It is forbidden to specify block composed value at the same line as key`

## 🔍 问题原因

在 YAML 中，如果值包含冒号 `:`，必须用引号括起来，否则 YAML 解析器会将其误认为是新的键值对。

### 错误的配置
```yaml
auth:
  session-redis-key-prefix: auth:session:    # ❌ 错误：冒号未加引号
```

YAML 解析器会把 `auth:session:` 解析为：
- `auth` 是键
- `session` 被误认为是新键
- 最后的 `:` 导致语法错误

### 正确的配置
```yaml
auth:
  session-redis-key-prefix: "auth:session:"  # ✅ 正确：用引号括起来
```

---

## ✅ 已修复的配置

### 1. session-redis-key-prefix
```yaml
# 修复前
session-redis-key-prefix: auth:session:      # ❌

# 修复后
session-redis-key-prefix: "auth:session:"    # ✅
```

### 2. admin-phones
```yaml
# 修复前
admin-phones: 15296711325                    # 可以不加引号

# 修复后（推荐）
admin-phones: "15296711325"                  # ✅ 加引号更安全
```

### 3. chat:redis-key-prefix
```yaml
# 修复前
redis-key-prefix: "chat:session:"            # ✅ 已经有引号
```

### 4. dms-base-url
```yaml
# URL 中有冒号，必须加引号
dms-base-url: "http://superapp.51eanj.com:82/eaju_app_service/eajudms/DmsInterface"
# 或
dms-base-url: http://superapp.51eanj.com:82/eaju_app_service/eajudms/DmsInterface
# URL 通常可以不加引号，但加引号更安全
```

---

## 📝 YAML 字符串引号规则

### 必须加引号的情况
1. **包含冒号**：`"auth:session:"`
2. **包含特殊字符**：`"@#$%^&*"`
3. **包含空格**：`"hello world"`
4. **以数字开头的特殊值**：`"123abc"`
5. **布尔值字符串**：`"true"`, `"false"`, `"yes"`, `"no"`

### 可以不加引号的情况
1. **纯字母数字**：`hello123`
2. **普通字符串**：`hello_world`
3. **标准数字**：`123`, `3.14`
4. **布尔值**：`true`, `false`

### 最佳实践
**建议所有字符串都加引号**，可以避免大多数 YAML 解析问题！

---

## 🔧 验证 YAML 语法

```bash
# 使用 Python 验证
python3 -c "import yaml; yaml.safe_load(open('application-test.yml'))"

# 或使用在线工具
# https://www.yamllint.com/
```

---

## ✅ 修复结果

配置文件已通过 YAML 语法验证，可以正常启动！

```bash
java -jar target/eaju-ai-api-1.0.0-SNAPSHOT.jar --spring.profiles.active=test
```
