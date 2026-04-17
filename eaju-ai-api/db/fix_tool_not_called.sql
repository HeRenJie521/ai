-- 修复工具调用问题：AI 不调用 getAppDispatchList 工具
-- 执行时间：2026-04-17

BEGIN;

-- 1. 查看当前配置
SELECT id, name, label, description, enabled 
FROM ai_tool 
WHERE name = 'getAppDispatchList';

-- 2. 更新工具描述（关键！让 AI 知道什么时候调用）
UPDATE ai_tool 
SET description = '查询工单列表接口。当用户想要查看、查询、统计工单、订单、任务、派工单列表时调用此工具。支持按日期范围筛选。典型场景：查下我今天有多少单子、查看我的工单列表、显示待处理的派工、统计本周完成的订单等。'
WHERE name = 'getAppDispatchList';

-- 3. 优化参数 Schema（让 AI 知道如何提取日期信息）
UPDATE ai_tool 
SET params_schema_json = '{
  "type": "object",
  "properties": {
    "dispatchTimeFm": {
      "type": "string",
      "description": "查询开始日期，格式 YYYY-MM-DD。用户说今天、最近、本周等时需要转换"
    },
    "dispatchTimeTo": {
      "type": "string",
      "description": "查询结束日期，格式 YYYY-MM-DD。用户说今天、最近、本周等时需要转换"
    }
  },
  "required": []
}'
WHERE name = 'getAppDispatchList';

-- 4. 查看应用绑定情况（确认工具已绑定到应用）
SELECT 
    app.id as app_id,
    app.name as app_name,
    app.enabled as app_enabled,
    tool.id as tool_id,
    tool.name as tool_name,
    tool.enabled as tool_enabled,
    binding.sort_order
FROM ai_app app
JOIN ai_app_tool binding ON app.id = binding.app_id
JOIN ai_tool tool ON binding.tool_id = tool.id
WHERE tool.name = 'getAppDispatchList';

-- 5. 验证更新结果
SELECT id, name, label, description, params_schema_json 
FROM ai_tool 
WHERE name = 'getAppDispatchList';

COMMIT;

-- 执行后请重启应用，然后测试对话："查下我今天有多少个单子"
