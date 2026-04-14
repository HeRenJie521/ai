-- Kimi：部分模型仅接受 temperature=1（否则上游 400）
UPDATE llm_provider_config
SET inference_defaults_json = (
        COALESCE(inference_defaults_json::jsonb, '{}'::jsonb)
        || '{"temperature": 1}'::jsonb
    )::text,
    updated_at = NOW()
WHERE UPPER(code) = 'KIMI';
