# EmbedView 用户数据缓存功能 - 部署说明

## 功能概述

本次更新实现了 EmbedView 嵌入页面的用户数据缓存功能，主要包含以下特性：

### 1. EmbedView 前端登录流程
- **输入参数**：`aid`（应用 ID）和 `phone`（手机号）
- **登录流程**：
  1. 前端调用后端 `/api/embed/app-login` 接口
  2. 后端调用 DMS `appUserLogin` 接口进行登录验证（**loginType=2，免密登录**）
  3. **登录失败**：页面上所有功能不可用，禁止发送消息
  4. **登录成功**：
     - 根据 `user_context_field` 表中配置的字段，从 DMS 登录响应中解析值
     - 将解析后的 key-value 缓存到 Redis（key: `ctx:{jti}`）
     - 将所有缓存的 key-value 返回给前端（仅用于测试展示）
     - 前端点击右上角用户图标可查看缓存数据

### 2. 工具调用复用策略（智能缓存）
- **第一次查询**：用户问"有哪些单子" → AI 调用工具查询 → 返回结果
- **第二次追问**：用户基于已查到的数据继续问（如"第一个单子的金额是多少"）→ AI 直接用缓存的数据回答，**不再调用工具**
- **重新查询**：用户明确要求重新查（如"重新查一下单子"、"帮我更新下单子信息"）→ AI 再次调用工具
- 通过系统提示词指导 LLM 的行为，优化工具调用逻辑，避免重复查询

### 3. 后端管理页面登录
- 调用 DMS 登录接口
- 成功后自动缓存用户数据字段到 Redis
- ✅ 已实现（无需修改）

### 4. 用户数据列表测试功能
- 管理后台可测试字段解析逻辑
- 从 Redis 获取当前登录用户的 DMS 登录数据进行测试
- ✅ 已实现（无需修改）

### 5. 接口管理入参设置
- 配置接口参数时，可选择参数来源为"用户数据"
- 调用工具时，自动从 Redis 获取对应字段值填充参数
- ✅ 已实现（无需修改）

### 6. 工具调用日志
- 自动打印接口入参和出参（**DEBUG 级别**）
- 需要在日志配置中开启 DEBUG 级别才能查看

---

## 修改文件清单

### 后端修改

| 文件路径 | 修改内容 |
|---------|---------|
| `EmbedAuthService.java` | 修改 `appEmbedLogin` 方法，增加 DMS 登录调用和用户数据缓存逻辑 |
| `AuthService.java` | 将 `resolvePhone`和`resolveDisplayName` 方法改为 `public`，供 EmbedAuthService 复用 |
| `LoginResponseDto.java` | 新增 `userContext` 字段，用于返回缓存的 key-value |

### 前端修改

| 文件路径 | 修改内容 |
|---------|---------|
| `EmbedView.vue` | 新增用户上下文展示面板，登录失败时禁用所有功能 |
| `auth.ts` | 新增 `AppEmbedLoginResult` 类型，包含 `userContext` 字段 |
| `mobile.html` | 更新嵌入代码示例，添加参数说明 |

---

## 部署步骤

### 1. 数据库检查

确认以下表已存在：
- `user_context_field` - 用户数据字段配置表
- `ai_tool` - AI 工具定义表

**本次更新不涉及数据库表结构变更。**

### 2. 后端部署

```bash
cd eaju-ai-api
mvn clean package
# 重启应用
```

### 3. 前端部署

```bash
cd eaju-ai-web
npm install
npm run build
# 部署 dist 目录到服务器
```

### 4. 配置用户数据字段

登录管理后台，进入"用户数据"页面，配置需要从 DMS 登录响应中提取的字段：

| 字段 Key | 显示名 | 字段类型 | 解析表达式 | 说明 |
|---------|-------|---------|-----------|------|
| `esusUserNameCn` | 用户姓名 | String | `data.esusUserNameCn` | 从 DMS 响应中提取用户姓名 |
| `esusMobile` | 手机号 | String | `data.esusMobile` | 从 DMS 响应中提取手机号 |
| `department` | 部门 | String | `data.department` | 从 DMS 响应中提取部门 |
| `employeeId` | 工号 | String | `data.employeeId` | 从 DMS 响应中提取工号 |

**解析表达式说明**：
- 使用点号路径（dot-notation）从 JSON 中提取值
- 例如：`data.esusMobile` 表示提取 `{"data": {"esusMobile": "13800138000"}}` 中的手机号

### 5. 测试验证

#### 5.1 访问测试页面

打开 `mobile.html` 或在浏览器中访问：
```
http://localhost:5173/embed?aid=1&phone=15296711325&username=测试用户
```

#### 5.2 验证登录成功

- 页面正常加载，显示 AI 助手标题
- 点击右上角用户图标，可查看缓存的用户上下文数据
- 可以正常发送消息

#### 5.3 验证登录失败

修改 `mobile.html` 中的手机号为一个无效的号码：
- 页面显示"登录失败"错误信息
- 输入框显示"登录失败，功能不可用"
- 所有按钮（发送、语音、深度思考）均被禁用

#### 5.4 验证工具调用

在接口管理中配置一个工具，参数来源设置为"用户数据"：
1. 调用工具时，查看后端日志
2. 确认入参中包含了从 Redis 获取的用户数据字段值
3. 确认出参也正常打印

---

## 日志排查

### 工具调用日志

调用工具时，后端会自动打印以下日志（**DEBUG 级别**）：

**开启 DEBUG 日志级别**（application.yml 或 application.properties）：

```yaml
logging:
  level:
    com.eaju.ai.service.ToolCallExecutor: DEBUG
```

**日志示例**：

```
[工具调用] 工具=菜单查询 (miniAppMenuFunctionQuery)
[工具调用] LLM 传入参数：{"menuId": "123"}
[工具调用] 用户上下文 keys: [esusUserNameCn, esusMobile, department]
[工具调用] 用户上下文 values: {esusUserNameCn=张三，esusMobile=13800138000, department=研发部}
[参数解析] key=userId 来源=用户上下文 fieldKey=esusMobile 取到值=13800138000
[工具调用] 请求：POST http://dms.example.com/api/menu
[工具调用] 请求体：{"userId": "13800138000", "menuId": "123"}
[工具调用] 响应状态：200 OK
[工具调用] 响应体：{"returnCode": 200, "data": {...}}
```

**注意**：默认日志级别为 INFO，如需查看工具调用详情，请在测试环境开启 DEBUG 级别。

### 登录日志

```
AppEmbedLogin 成功：appId=1, userId=15296711325, phone=15296711325, username=何仁杰
AppEmbedLogin: 缓存用户上下文：jti=abc123 keys=[esusUserNameCn, esusMobile] values={esusUserNameCn=何仁杰，esusMobile=15296711325}
```

---

## 常见问题

### Q1: 登录失败，提示"登录服务返回异常状态"
**检查**：
1. DMS 服务是否正常
2. `app.auth.dms-base-url` 配置是否正确
3. DMS 侧是否配置了免密验证（因为登录时密码为空）

### Q2: 用户上下文为空
**检查**：
1. `user_context_field` 表中是否配置了启用的字段
2. `parseExpression` 路径是否正确
3. DMS 登录响应中是否包含对应字段

### Q3: 工具调用时参数值为 null
**检查**：
1. Redis 中是否存在 `ctx:{jti}` 缓存
2. 工具参数配置中 `valueSource` 是否设置为 `context`
3. `fieldKey` 是否与用户数据字段的 key 一致

---

## 嵌入代码示例

### 基础嵌入

```html
<iframe
    src="https://chat.example.com/embed?aid=1&phone=15296711325"
    width="100%"
    height="100%"
    style="border:none;"
    allow="clipboard-write">
</iframe>
```

### 带用户名和额外参数

```html
<iframe
    src="https://chat.example.com/embed?aid=1&phone=15296711325&username=张三&department=研发部&employeeId=E001"
    width="100%"
    height="100%"
    style="border:none;"
    allow="clipboard-write">
</iframe>
```

**参数说明**：
- `aid`: 应用 ID（必填）
- `phone`: 手机号（必填），用于 DMS 登录
- `username`: 用户名（可选）
- 其他参数：自动作为 `extraContext` 透传，但仅在 `user_context_field` 表中配置的字段才会被存入缓存

---

## 回滚方案

如需回滚到旧版本：

### 后端回滚
```bash
cd eaju-ai-api
git checkout HEAD -- src/main/java/com/eaju/ai/service/EmbedAuthService.java
git checkout HEAD -- src/main/java/com/eaju/ai/service/AuthService.java
git checkout HEAD -- src/main/java/com/eaju/ai/dto/auth/LoginResponseDto.java
mvn clean package
# 重启应用
```

### 前端回滚
```bash
cd eaju-ai-web
git checkout HEAD -- src/views/EmbedView.vue
git checkout HEAD -- src/api/auth.ts
git checkout HEAD -- mobile.html
npm run build
# 重新部署
```
