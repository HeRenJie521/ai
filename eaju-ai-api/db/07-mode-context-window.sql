-- =============================================================================
-- 07-mode-context-window.sql
-- 为各提供方 modes_json 添加 contextWindow 字段（单位：token），并统一升级至对象格式。
--
-- contextWindow 依据各厂商官方文档填写（截至 2026-04）：
--   DeepSeek-V3 / R1:   65 536  (64K)
--   Qwen3.x 系列:       131 072 (128K)
--   Moonshot V1:        8K / 32K / 128K（名称直接对应）
--   Kimi K2 / K2.5:     131 072 (128K)
--   Gemini 2.5 / 3.x:  1 048 576 (1M)
--   ERNIE 128k:         131 072 (128K)
--   ERNIE 32k:           32 768 (32K)
--   MiniMax Abab6.5t/7: 245 760 (240K)
--   MiniMax M1/M2.x/Text-01: 1 000 000 (~1M)
--
-- 执行示例：
--   psql -U postgres -h localhost -d eaju_ai -f db/07-mode-context-window.sql
-- =============================================================================

-- ── DeepSeek ─────────────────────────────────────────────────────────────────
UPDATE llm_provider_config SET modes_json = '{
  "deepseek-chat":     {"upstreamModel":"deepseek-chat",     "textGeneration":true,"deepThinking":true,"vision":false,"contextWindow":65536},
  "deepseek-reasoner": {"upstreamModel":"deepseek-reasoner", "textGeneration":true,"deepThinking":true,"vision":false,"contextWindow":65536}
}'
WHERE code = 'DEEPSEEK';

-- ── 通义千问（百炼 / DashScope Qwen3.x 系列，均为 128K）────────────────────
UPDATE llm_provider_config SET modes_json = '{
  "qwen3.6-plus":                        {"upstreamModel":"qwen3.6-plus",                        "textGeneration":true,"deepThinking":true,"vision":false,"contextWindow":131072},
  "qwen3.6-plus-2026-04-02(Qwen3.6)":   {"upstreamModel":"qwen3.6-plus-2026-04-02(Qwen3.6)",   "textGeneration":true,"deepThinking":true,"vision":false,"contextWindow":131072},
  "qwen3.5-plus":                        {"upstreamModel":"qwen3.5-plus",                        "textGeneration":true,"deepThinking":true,"vision":false,"contextWindow":131072},
  "qwen3.5-plus-2026-02-15(Qwen3.5)":   {"upstreamModel":"qwen3.5-plus-2026-02-15(Qwen3.5)",   "textGeneration":true,"deepThinking":true,"vision":false,"contextWindow":131072},
  "qwen3.5-flash":                       {"upstreamModel":"qwen3.5-flash",                       "textGeneration":true,"deepThinking":true,"vision":false,"contextWindow":131072},
  "qwen3.5-flash-2026-02-23(Qwen3.5)":  {"upstreamModel":"qwen3.5-flash-2026-02-23(Qwen3.5)",  "textGeneration":true,"deepThinking":true,"vision":false,"contextWindow":131072}
}'
WHERE code = 'QWEN';

-- ── 月之暗面 Kimi ─────────────────────────────────────────────────────────────
UPDATE llm_provider_config SET modes_json = '{
  "moonshot-v1-8k":         {"upstreamModel":"moonshot-v1-8k",         "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":8192},
  "moonshot-v1-32k":        {"upstreamModel":"moonshot-v1-32k",        "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":32768},
  "moonshot-v1-128k":       {"upstreamModel":"moonshot-v1-128k",       "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":131072},
  "kimi-k2-0711-preview":   {"upstreamModel":"kimi-k2-0711-preview",   "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":131072},
  "kimi-k2-0905-preview":   {"upstreamModel":"kimi-k2-0905-preview",   "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":131072},
  "kimi-k2-turbo-preview":  {"upstreamModel":"kimi-k2-turbo-preview",  "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":131072},
  "kimi-k2-thinking":       {"upstreamModel":"kimi-k2-thinking",       "textGeneration":true,"deepThinking":true, "vision":false,"contextWindow":131072},
  "kimi-k2-thinking-turbo": {"upstreamModel":"kimi-k2-thinking-turbo", "textGeneration":true,"deepThinking":true, "vision":false,"contextWindow":131072},
  "kimi-k2.5":              {"upstreamModel":"kimi-k2.5",              "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":131072}
}'
WHERE code = 'KIMI';

-- ── Google Gemini（2.5 及以上系列上下文均为 1M）──────────────────────────────
UPDATE llm_provider_config SET modes_json = '{
  "gemini-3.1-pro-preview":           {"upstreamModel":"gemini-3.1-pro-preview",           "textGeneration":true,"deepThinking":false,"vision":true,"contextWindow":1048576},
  "gemini-3.1-pro-preview-customtools":{"upstreamModel":"gemini-3.1-pro-preview-customtools","textGeneration":true,"deepThinking":false,"vision":true,"contextWindow":1048576},
  "gemini-3.1-flash-lite-preview":    {"upstreamModel":"gemini-3.1-flash-lite-preview",    "textGeneration":true,"deepThinking":false,"vision":true,"contextWindow":1048576},
  "gemini-3-pro-image-preview":       {"upstreamModel":"gemini-3-pro-image-preview",       "textGeneration":true,"deepThinking":false,"vision":true,"contextWindow":1048576},
  "gemini-3-flash-preview":           {"upstreamModel":"gemini-3-flash-preview",           "textGeneration":true,"deepThinking":false,"vision":true,"contextWindow":1048576},
  "gemini-2.5-pro":                   {"upstreamModel":"gemini-2.5-pro",                   "textGeneration":true,"deepThinking":false,"vision":true,"contextWindow":1048576},
  "gemini-2.5-flash":                 {"upstreamModel":"gemini-2.5-flash",                 "textGeneration":true,"deepThinking":false,"vision":true,"contextWindow":1048576},
  "gemini-2.5-flash-lite":            {"upstreamModel":"gemini-2.5-flash-lite",            "textGeneration":true,"deepThinking":false,"vision":true,"contextWindow":1048576},
  "gemini-2.5-flash-image":           {"upstreamModel":"gemini-2.5-flash-image",           "textGeneration":true,"deepThinking":false,"vision":true,"contextWindow":1048576},
  "gemini-flash-latest":              {"upstreamModel":"gemini-flash-latest",              "textGeneration":true,"deepThinking":false,"vision":true,"contextWindow":1048576},
  "gemini-flash-lite-latest":         {"upstreamModel":"gemini-flash-lite-latest",         "textGeneration":true,"deepThinking":false,"vision":true,"contextWindow":1048576}
}'
WHERE code = 'GEMINI';

-- ── 百度千帆（ERNIE，上下文大小写在模型名中）─────────────────────────────────
UPDATE llm_provider_config SET modes_json = '{
  "ernie-3.5-128k":       {"upstreamModel":"ernie-3.5-128k",       "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":131072},
  "ernie-4.0-turbo-128k": {"upstreamModel":"ernie-4.0-turbo-128k", "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":131072},
  "ernie-4.5-turbo-128k": {"upstreamModel":"ernie-4.5-turbo-128k", "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":131072},
  "ernie-4.5-turbo-32k":  {"upstreamModel":"ernie-4.5-turbo-32k",  "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":32768},
  "ernie-lite-pro-128k":  {"upstreamModel":"ernie-lite-pro-128k",  "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":131072},
  "ernie-speed-pro-128k": {"upstreamModel":"ernie-speed-pro-128k", "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":131072},
  "ernie-x1-turbo-32k":   {"upstreamModel":"ernie-x1-turbo-32k",   "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":32768}
}'
WHERE code = 'QIANFAN';

-- ── MiniMax ───────────────────────────────────────────────────────────────────
UPDATE llm_provider_config SET modes_json = '{
  "Abab6.5t-Chat":          {"upstreamModel":"Abab6.5t-Chat",          "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":245760},
  "Abab7-chat-preview":     {"upstreamModel":"Abab7-chat-preview",     "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":245760},
  "MiniMax-M1":             {"upstreamModel":"MiniMax-M1",             "textGeneration":true,"deepThinking":true, "vision":false,"contextWindow":1048576},
  "MiniMax-M2-Her":         {"upstreamModel":"MiniMax-M2-Her",         "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":1048576},
  "MiniMax-M2.1-Lightning": {"upstreamModel":"MiniMax-M2.1-Lightning", "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":1048576},
  "MiniMax-M2.1":           {"upstreamModel":"MiniMax-M2.1",           "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":1048576},
  "MiniMax-M2.5-Lightning": {"upstreamModel":"MiniMax-M2.5-Lightning", "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":1048576},
  "MiniMax-M2.5":           {"upstreamModel":"MiniMax-M2.5",           "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":1048576},
  "MiniMax-M2.7-highspeed": {"upstreamModel":"MiniMax-M2.7-highspeed", "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":1048576},
  "MiniMax-M2.7":           {"upstreamModel":"MiniMax-M2.7",           "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":1048576},
  "MiniMax-M2":             {"upstreamModel":"MiniMax-M2",             "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":1048576},
  "Minimax-Text-01":        {"upstreamModel":"Minimax-Text-01",        "textGeneration":true,"deepThinking":false,"vision":false,"contextWindow":1048576}
}'
WHERE code = 'MINIMAX';
