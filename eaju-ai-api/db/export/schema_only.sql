--
-- PostgreSQL database dump
--

\restrict q5cJibcziVQzrAVsPL0Jc67XH302DreOgqmQAzvsTNBkutvJtlvulQDPF7jxunF

-- Dumped from database version 17.9
-- Dumped by pg_dump version 17.9

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

ALTER TABLE IF EXISTS ONLY public.llm_model DROP CONSTRAINT IF EXISTS llm_model_provider_id_fkey;
ALTER TABLE IF EXISTS ONLY public.api_key DROP CONSTRAINT IF EXISTS api_key_app_id_fkey;
ALTER TABLE IF EXISTS ONLY public.ai_app_tool DROP CONSTRAINT IF EXISTS ai_app_tool_tool_id_fkey;
ALTER TABLE IF EXISTS ONLY public.ai_app_tool DROP CONSTRAINT IF EXISTS ai_app_tool_app_id_fkey;
ALTER TABLE IF EXISTS ONLY public.ai_app DROP CONSTRAINT IF EXISTS ai_app_llm_model_id_fkey;
DROP INDEX IF EXISTS public.uq_api_key_secret_hash;
DROP INDEX IF EXISTS public.idx_user_context_field_enabled;
DROP INDEX IF EXISTS public.idx_llm_provider_config_enabled_sort;
DROP INDEX IF EXISTS public.idx_llm_model_provider_id;
DROP INDEX IF EXISTS public.idx_llm_model_enabled_sort;
DROP INDEX IF EXISTS public.idx_chat_turn_session_id;
DROP INDEX IF EXISTS public.idx_chat_turn_llm_model_id;
DROP INDEX IF EXISTS public.idx_chat_turn_integration_id;
DROP INDEX IF EXISTS public.idx_chat_turn_created_at;
DROP INDEX IF EXISTS public.idx_chat_turn_app_id;
DROP INDEX IF EXISTS public.idx_chat_turn_api_key_id;
DROP INDEX IF EXISTS public.idx_chat_conversation_user_last;
DROP INDEX IF EXISTS public.idx_chat_conversation_llm_model_id;
DROP INDEX IF EXISTS public.idx_chat_conversation_last_provider_display_name;
DROP INDEX IF EXISTS public.idx_chat_conversation_integration_id;
DROP INDEX IF EXISTS public.idx_chat_conversation_deleted_at;
DROP INDEX IF EXISTS public.idx_chat_conversation_app_id;
DROP INDEX IF EXISTS public.idx_chat_conversation_api_key_id;
DROP INDEX IF EXISTS public.idx_api_key_default_model_id;
DROP INDEX IF EXISTS public.idx_ai_tool_enabled;
DROP INDEX IF EXISTS public.idx_ai_app_tool_tool_id;
DROP INDEX IF EXISTS public.idx_ai_app_tool_app_id;
DROP INDEX IF EXISTS public.idx_ai_app_llm_model_id;
DROP INDEX IF EXISTS public.idx_ai_app_deleted;
ALTER TABLE IF EXISTS ONLY public.user_context_field DROP CONSTRAINT IF EXISTS user_context_field_pkey;
ALTER TABLE IF EXISTS ONLY public.chat_conversation DROP CONSTRAINT IF EXISTS uk875sxlmwjsxujqg8g0x0hj85;
ALTER TABLE IF EXISTS ONLY public.llm_provider_config DROP CONSTRAINT IF EXISTS llm_provider_config_pkey;
ALTER TABLE IF EXISTS ONLY public.llm_provider_config DROP CONSTRAINT IF EXISTS llm_provider_config_code_key;
ALTER TABLE IF EXISTS ONLY public.llm_model DROP CONSTRAINT IF EXISTS llm_model_provider_id_name_key;
ALTER TABLE IF EXISTS ONLY public.llm_model DROP CONSTRAINT IF EXISTS llm_model_pkey;
ALTER TABLE IF EXISTS ONLY public.chat_turn DROP CONSTRAINT IF EXISTS chat_turn_pkey;
ALTER TABLE IF EXISTS ONLY public.chat_conversation DROP CONSTRAINT IF EXISTS chat_conversation_user_id_session_id_key;
ALTER TABLE IF EXISTS ONLY public.chat_conversation DROP CONSTRAINT IF EXISTS chat_conversation_pkey;
ALTER TABLE IF EXISTS ONLY public.api_key DROP CONSTRAINT IF EXISTS api_key_pkey;
ALTER TABLE IF EXISTS ONLY public.api_definition DROP CONSTRAINT IF EXISTS api_definition_pkey;
ALTER TABLE IF EXISTS ONLY public.ai_tool DROP CONSTRAINT IF EXISTS ai_tool_pkey;
ALTER TABLE IF EXISTS ONLY public.ai_app_tool DROP CONSTRAINT IF EXISTS ai_app_tool_pkey;
ALTER TABLE IF EXISTS ONLY public.ai_app DROP CONSTRAINT IF EXISTS ai_app_pkey;
ALTER TABLE IF EXISTS public.user_context_field ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.llm_provider_config ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.llm_model ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.chat_turn ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.chat_conversation ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.api_key ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.api_definition ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.ai_tool ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.ai_app_tool ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.ai_app ALTER COLUMN id DROP DEFAULT;
DROP SEQUENCE IF EXISTS public.user_context_field_id_seq;
DROP TABLE IF EXISTS public.user_context_field;
DROP SEQUENCE IF EXISTS public.llm_provider_config_id_seq;
DROP TABLE IF EXISTS public.llm_provider_config;
DROP SEQUENCE IF EXISTS public.llm_model_id_seq;
DROP TABLE IF EXISTS public.llm_model;
DROP SEQUENCE IF EXISTS public.chat_turn_id_seq;
DROP TABLE IF EXISTS public.chat_turn;
DROP SEQUENCE IF EXISTS public.chat_conversation_id_seq;
DROP TABLE IF EXISTS public.chat_conversation;
DROP SEQUENCE IF EXISTS public.api_key_id_seq;
DROP TABLE IF EXISTS public.api_key;
DROP SEQUENCE IF EXISTS public.api_definition_id_seq;
DROP TABLE IF EXISTS public.api_definition;
DROP SEQUENCE IF EXISTS public.ai_tool_id_seq;
DROP TABLE IF EXISTS public.ai_tool;
DROP SEQUENCE IF EXISTS public.ai_app_tool_id_seq;
DROP TABLE IF EXISTS public.ai_app_tool;
DROP SEQUENCE IF EXISTS public.ai_app_id_seq;
DROP TABLE IF EXISTS public.ai_app;
SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: ai_app; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_app (
    id bigint NOT NULL,
    name character varying(128) NOT NULL,
    welcome_text text,
    suggestions text,
    system_role text,
    system_task text,
    system_constraints text,
    llm_model_id bigint,
    deleted boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ai_app; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.ai_app IS 'AI 应用配置';


--
-- Name: COLUMN ai_app.welcome_text; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.ai_app.welcome_text IS '开场白文本';


--
-- Name: COLUMN ai_app.suggestions; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.ai_app.suggestions IS '推荐问题 JSON 字符串';


--
-- Name: COLUMN ai_app.system_role; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.ai_app.system_role IS 'Agent 角色设定';


--
-- Name: COLUMN ai_app.system_task; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.ai_app.system_task IS 'Agent 任务指令';


--
-- Name: COLUMN ai_app.system_constraints; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.ai_app.system_constraints IS 'Agent 限制条件';


--
-- Name: COLUMN ai_app.llm_model_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.ai_app.llm_model_id IS '应用绑定的模型 ID，关联 llm_model.id';


--
-- Name: ai_app_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ai_app_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ai_app_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ai_app_id_seq OWNED BY public.ai_app.id;


--
-- Name: ai_app_tool; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_app_tool (
    id bigint NOT NULL,
    app_id bigint NOT NULL,
    tool_id bigint NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    call_strategy character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE ai_app_tool; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.ai_app_tool IS 'AI 应用与工具的绑定关系';


--
-- Name: COLUMN ai_app_tool.call_strategy; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.ai_app_tool.call_strategy IS '调用策略：PARALLEL（并行）、SEQUENTIAL（串行）';


--
-- Name: ai_app_tool_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ai_app_tool_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ai_app_tool_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ai_app_tool_id_seq OWNED BY public.ai_app_tool.id;


--
-- Name: ai_tool; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_tool (
    id bigint NOT NULL,
    name character varying(128) NOT NULL,
    label character varying(128) NOT NULL,
    description text,
    headers_json text,
    body_template text,
    method_name character varying(128),
    data_params_json text,
    response_params_json text,
    params_schema_json text,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    api_definition_id bigint
);


--
-- Name: TABLE ai_tool; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.ai_tool IS 'AI 工具（Function Calling）';


--
-- Name: COLUMN ai_tool.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.ai_tool.name IS '工具标识（英文）';


--
-- Name: COLUMN ai_tool.label; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.ai_tool.label IS '工具显示名称（中文）';


--
-- Name: COLUMN ai_tool.data_params_json; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.ai_tool.data_params_json IS '入参配置 JSON';


--
-- Name: COLUMN ai_tool.response_params_json; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.ai_tool.response_params_json IS '出参配置 JSON';


--
-- Name: COLUMN ai_tool.params_schema_json; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.ai_tool.params_schema_json IS '参数 Schema JSON（用于 LLM 工具定义）';


--
-- Name: ai_tool_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ai_tool_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ai_tool_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ai_tool_id_seq OWNED BY public.ai_tool.id;


--
-- Name: api_definition; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.api_definition (
    id bigint NOT NULL,
    system_name character varying(128) NOT NULL,
    request_url text NOT NULL,
    http_method character varying(16) DEFAULT 'GET'::character varying NOT NULL,
    content_type character varying(128),
    remark text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE api_definition; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.api_definition IS '接口定义（供工具调用参考）';


--
-- Name: api_definition_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.api_definition_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: api_definition_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.api_definition_id_seq OWNED BY public.api_definition.id;


--
-- Name: api_key; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.api_key (
    id bigint NOT NULL,
    name character varying(128) NOT NULL,
    secret_hash character varying(64) NOT NULL,
    secret_prefix character varying(64) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    deleted boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    type integer DEFAULT 1 NOT NULL,
    default_model_id bigint,
    allowed_origins character varying(1000),
    welcome_text text,
    suggestions text,
    system_role text,
    system_task text,
    system_constraints text,
    app_id bigint
);


--
-- Name: TABLE api_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.api_key IS '开放 API 调用密钥 / 嵌入网站集成配置；明文仅创建时返回一次，库中仅存 SHA-256';


--
-- Name: COLUMN api_key.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.api_key.type IS '集成类型：1=API_KEY（默认） 2=WEB_EMBED（嵌入网站）';


--
-- Name: COLUMN api_key.default_model_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.api_key.default_model_id IS '默认模型 ID，关联 llm_model.id';


--
-- Name: COLUMN api_key.allowed_origins; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.api_key.allowed_origins IS 'WEB_EMBED：允许嵌入的来源域名，逗号分隔；为空表示不限';


--
-- Name: COLUMN api_key.welcome_text; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.api_key.welcome_text IS 'WEB_EMBED：开场白文本';


--
-- Name: COLUMN api_key.suggestions; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.api_key.suggestions IS 'WEB_EMBED：推荐问题 JSON 字符串，例如：["问题 1", "问题 2"]';


--
-- Name: COLUMN api_key.system_role; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.api_key.system_role IS 'WEB_EMBED：Agent 角色设定';


--
-- Name: COLUMN api_key.system_task; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.api_key.system_task IS 'WEB_EMBED：Agent 任务指令';


--
-- Name: COLUMN api_key.system_constraints; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.api_key.system_constraints IS 'WEB_EMBED：Agent 限制条件';


--
-- Name: COLUMN api_key.app_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.api_key.app_id IS '绑定的 AI 应用 ID；非 null 时调用自动加载该应用系统提示和工具';


--
-- Name: api_key_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.api_key_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: api_key_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.api_key_id_seq OWNED BY public.api_key.id;


--
-- Name: chat_conversation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chat_conversation (
    id bigint NOT NULL,
    user_id character varying(32) NOT NULL,
    session_id character varying(128) NOT NULL,
    title character varying(200) DEFAULT '新对话'::character varying NOT NULL,
    last_message_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    last_provider_code character varying(64),
    last_provider_display_name character varying(128),
    last_mode_key character varying(512),
    api_key_id bigint,
    integration_id bigint,
    app_id bigint,
    llm_model_id bigint,
    deleted_at timestamp with time zone
);


--
-- Name: TABLE chat_conversation; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.chat_conversation IS '左侧会话列表；user_id 为登录接口返回的手机号';


--
-- Name: COLUMN chat_conversation.last_provider_display_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.chat_conversation.last_provider_display_name IS '最后使用的模型提供商显示名称（如百炼、DeepSeek）';


--
-- Name: COLUMN chat_conversation.api_key_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.chat_conversation.api_key_id IS '会话归属的 API Key（type=1）';


--
-- Name: COLUMN chat_conversation.integration_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.chat_conversation.integration_id IS '集成 ID（type=2）';


--
-- Name: COLUMN chat_conversation.app_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.chat_conversation.app_id IS 'AI 应用嵌入 ID（ai_app.id），用于按应用统计';


--
-- Name: COLUMN chat_conversation.llm_model_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.chat_conversation.llm_model_id IS '会话最后使用的模型 ID，关联 llm_model.id';


--
-- Name: COLUMN chat_conversation.deleted_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.chat_conversation.deleted_at IS '逻辑删除时间，非空表示已删除';


--
-- Name: chat_conversation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.chat_conversation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: chat_conversation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.chat_conversation_id_seq OWNED BY public.chat_conversation.id;


--
-- Name: chat_turn; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chat_turn (
    id bigint NOT NULL,
    session_id character varying(128),
    user_id character varying(128),
    provider character varying(64) NOT NULL,
    model character varying(256),
    client_messages_json text,
    request_messages_json text,
    assistant_content text,
    reasoning_content text,
    upstream_message_id character varying(128),
    finish_reason character varying(64),
    prompt_tokens integer,
    completion_tokens integer,
    total_tokens integer,
    stream_mode boolean DEFAULT false NOT NULL,
    api_key_id bigint,
    integration_id bigint,
    app_id bigint,
    llm_model_id bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE chat_turn; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.chat_turn IS '单次对话轮次：记录用户侧消息、完整请求上下文、助手回复、token 与上游元数据';


--
-- Name: COLUMN chat_turn.api_key_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.chat_turn.api_key_id IS '若请求使用 X-API-Key 鉴权则记录对应密钥 id（type=1）';


--
-- Name: COLUMN chat_turn.integration_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.chat_turn.integration_id IS '集成 ID：type=2 时等于 api_key_id，用于按集成统计';


--
-- Name: COLUMN chat_turn.app_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.chat_turn.app_id IS 'AI 应用嵌入 ID（ai_app.id），用于按应用统计用量';


--
-- Name: COLUMN chat_turn.llm_model_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.chat_turn.llm_model_id IS '本轮使用的模型 ID，关联 llm_model.id';


--
-- Name: chat_turn_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.chat_turn_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: chat_turn_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.chat_turn_id_seq OWNED BY public.chat_turn.id;


--
-- Name: llm_model; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.llm_model (
    id bigint NOT NULL,
    provider_id bigint NOT NULL,
    name character varying(256) NOT NULL,
    upstream_model_id character varying(256) NOT NULL,
    text_generation boolean DEFAULT true NOT NULL,
    deep_thinking boolean DEFAULT false NOT NULL,
    vision boolean DEFAULT false NOT NULL,
    stream_output boolean DEFAULT true NOT NULL,
    tool_call boolean DEFAULT true NOT NULL,
    force_thinking_enabled boolean DEFAULT false NOT NULL,
    temperature numeric(5,2),
    max_tokens integer,
    top_p numeric(5,2),
    top_k integer,
    frequency_penalty numeric(5,2),
    presence_penalty numeric(5,2),
    response_format character varying(64),
    thinking_mode boolean,
    context_window integer,
    sort_order integer DEFAULT 0 NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE llm_model; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.llm_model IS '大模型实例配置（每个模型单独配置）';


--
-- Name: COLUMN llm_model.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.llm_model.name IS '逻辑名（即 /chat 请求体 mode 参数值，也是前端展示名称）';


--
-- Name: COLUMN llm_model.upstream_model_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.llm_model.upstream_model_id IS '实际发往上游 API 的 model 字段值';


--
-- Name: COLUMN llm_model.text_generation; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.llm_model.text_generation IS '是否支持文本生成';


--
-- Name: COLUMN llm_model.deep_thinking; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.llm_model.deep_thinking IS '是否支持深度思考（同时表示支持 thinking API）';


--
-- Name: COLUMN llm_model.vision; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.llm_model.vision IS '是否支持视觉理解（图片）';


--
-- Name: COLUMN llm_model.stream_output; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.llm_model.stream_output IS '是否支持流式输出';


--
-- Name: COLUMN llm_model.tool_call; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.llm_model.tool_call IS '是否支持工具调用（Function Calling）';


--
-- Name: COLUMN llm_model.force_thinking_enabled; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.llm_model.force_thinking_enabled IS '是否强制开启 thinking（如 MiniMax 系列）';


--
-- Name: COLUMN llm_model.response_format; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.llm_model.response_format IS 'TEXT 或 JSON_OBJECT';


--
-- Name: llm_model_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.llm_model_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: llm_model_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.llm_model_id_seq OWNED BY public.llm_model.id;


--
-- Name: llm_provider_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.llm_provider_config (
    id bigint NOT NULL,
    code character varying(64) NOT NULL,
    display_name character varying(128) NOT NULL,
    api_key text DEFAULT ''::text NOT NULL,
    base_url text NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    force_temperature numeric(5,2),
    thinking_param_style character varying(32) DEFAULT 'openai'::character varying NOT NULL,
    json_mode_system_hint boolean DEFAULT false NOT NULL,
    strip_tool_call_index boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE llm_provider_config; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.llm_provider_config IS 'OpenAI 兼容 Chat Completions 的提供方配置';


--
-- Name: COLUMN llm_provider_config.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.llm_provider_config.code IS '与 POST /chat 请求体 provider 对应';


--
-- Name: COLUMN llm_provider_config.display_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.llm_provider_config.display_name IS '提供方显示名称（如：通义千问、DeepSeek）';


--
-- Name: COLUMN llm_provider_config.force_temperature; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.llm_provider_config.force_temperature IS '非空时强制覆盖请求中的 temperature（如 Kimi 只接受 1.0）';


--
-- Name: COLUMN llm_provider_config.thinking_param_style; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.llm_provider_config.thinking_param_style IS 'thinking 参数风格：openai（thinking.type=...）或 dashscope（enable_thinking=true/false）';


--
-- Name: COLUMN llm_provider_config.json_mode_system_hint; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.llm_provider_config.json_mode_system_hint IS '使用 JSON 模式时是否自动在 system message 中注入 "json" 关键词';


--
-- Name: COLUMN llm_provider_config.strip_tool_call_index; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.llm_provider_config.strip_tool_call_index IS '将历史 tool_calls 回传给模型前是否剥掉 index 字段';


--
-- Name: llm_provider_config_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.llm_provider_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: llm_provider_config_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.llm_provider_config_id_seq OWNED BY public.llm_provider_config.id;


--
-- Name: user_context_field; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_context_field (
    id bigint NOT NULL,
    field_key character varying(128) NOT NULL,
    label character varying(128) NOT NULL,
    field_type character varying(32) DEFAULT 'String'::character varying NOT NULL,
    parse_expression character varying(512),
    description text,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE user_context_field; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.user_context_field IS '用户上下文字段配置';


--
-- Name: COLUMN user_context_field.field_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.user_context_field.field_type IS '字段类型：String、Number、Boolean、Object、Array';


--
-- Name: COLUMN user_context_field.parse_expression; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.user_context_field.parse_expression IS '解析表达式（dot-notation 路径）';


--
-- Name: user_context_field_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_context_field_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_context_field_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_context_field_id_seq OWNED BY public.user_context_field.id;


--
-- Name: ai_app id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_app ALTER COLUMN id SET DEFAULT nextval('public.ai_app_id_seq'::regclass);


--
-- Name: ai_app_tool id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_app_tool ALTER COLUMN id SET DEFAULT nextval('public.ai_app_tool_id_seq'::regclass);


--
-- Name: ai_tool id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_tool ALTER COLUMN id SET DEFAULT nextval('public.ai_tool_id_seq'::regclass);


--
-- Name: api_definition id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_definition ALTER COLUMN id SET DEFAULT nextval('public.api_definition_id_seq'::regclass);


--
-- Name: api_key id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_key ALTER COLUMN id SET DEFAULT nextval('public.api_key_id_seq'::regclass);


--
-- Name: chat_conversation id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_conversation ALTER COLUMN id SET DEFAULT nextval('public.chat_conversation_id_seq'::regclass);


--
-- Name: chat_turn id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_turn ALTER COLUMN id SET DEFAULT nextval('public.chat_turn_id_seq'::regclass);


--
-- Name: llm_model id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.llm_model ALTER COLUMN id SET DEFAULT nextval('public.llm_model_id_seq'::regclass);


--
-- Name: llm_provider_config id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.llm_provider_config ALTER COLUMN id SET DEFAULT nextval('public.llm_provider_config_id_seq'::regclass);


--
-- Name: user_context_field id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_context_field ALTER COLUMN id SET DEFAULT nextval('public.user_context_field_id_seq'::regclass);


--
-- Name: ai_app ai_app_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_app
    ADD CONSTRAINT ai_app_pkey PRIMARY KEY (id);


--
-- Name: ai_app_tool ai_app_tool_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_app_tool
    ADD CONSTRAINT ai_app_tool_pkey PRIMARY KEY (id);


--
-- Name: ai_tool ai_tool_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_tool
    ADD CONSTRAINT ai_tool_pkey PRIMARY KEY (id);


--
-- Name: api_definition api_definition_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_definition
    ADD CONSTRAINT api_definition_pkey PRIMARY KEY (id);


--
-- Name: api_key api_key_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_key
    ADD CONSTRAINT api_key_pkey PRIMARY KEY (id);


--
-- Name: chat_conversation chat_conversation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_conversation
    ADD CONSTRAINT chat_conversation_pkey PRIMARY KEY (id);


--
-- Name: chat_conversation chat_conversation_user_id_session_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_conversation
    ADD CONSTRAINT chat_conversation_user_id_session_id_key UNIQUE (user_id, session_id);


--
-- Name: chat_turn chat_turn_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_turn
    ADD CONSTRAINT chat_turn_pkey PRIMARY KEY (id);


--
-- Name: llm_model llm_model_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.llm_model
    ADD CONSTRAINT llm_model_pkey PRIMARY KEY (id);


--
-- Name: llm_model llm_model_provider_id_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.llm_model
    ADD CONSTRAINT llm_model_provider_id_name_key UNIQUE (provider_id, name);


--
-- Name: llm_provider_config llm_provider_config_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.llm_provider_config
    ADD CONSTRAINT llm_provider_config_code_key UNIQUE (code);


--
-- Name: llm_provider_config llm_provider_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.llm_provider_config
    ADD CONSTRAINT llm_provider_config_pkey PRIMARY KEY (id);


--
-- Name: chat_conversation uk875sxlmwjsxujqg8g0x0hj85; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_conversation
    ADD CONSTRAINT uk875sxlmwjsxujqg8g0x0hj85 UNIQUE (user_id, session_id);


--
-- Name: user_context_field user_context_field_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_context_field
    ADD CONSTRAINT user_context_field_pkey PRIMARY KEY (id);


--
-- Name: idx_ai_app_deleted; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_app_deleted ON public.ai_app USING btree (deleted);


--
-- Name: idx_ai_app_llm_model_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_app_llm_model_id ON public.ai_app USING btree (llm_model_id);


--
-- Name: idx_ai_app_tool_app_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_app_tool_app_id ON public.ai_app_tool USING btree (app_id);


--
-- Name: idx_ai_app_tool_tool_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_app_tool_tool_id ON public.ai_app_tool USING btree (tool_id);


--
-- Name: idx_ai_tool_enabled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_tool_enabled ON public.ai_tool USING btree (enabled);


--
-- Name: idx_api_key_default_model_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_api_key_default_model_id ON public.api_key USING btree (default_model_id);


--
-- Name: idx_chat_conversation_api_key_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_conversation_api_key_id ON public.chat_conversation USING btree (api_key_id);


--
-- Name: idx_chat_conversation_app_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_conversation_app_id ON public.chat_conversation USING btree (app_id);


--
-- Name: idx_chat_conversation_deleted_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_conversation_deleted_at ON public.chat_conversation USING btree (deleted_at);


--
-- Name: idx_chat_conversation_integration_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_conversation_integration_id ON public.chat_conversation USING btree (integration_id);


--
-- Name: idx_chat_conversation_last_provider_display_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_conversation_last_provider_display_name ON public.chat_conversation USING btree (last_provider_display_name);


--
-- Name: idx_chat_conversation_llm_model_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_conversation_llm_model_id ON public.chat_conversation USING btree (llm_model_id);


--
-- Name: idx_chat_conversation_user_last; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_conversation_user_last ON public.chat_conversation USING btree (user_id, last_message_at DESC);


--
-- Name: idx_chat_turn_api_key_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_turn_api_key_id ON public.chat_turn USING btree (api_key_id);


--
-- Name: idx_chat_turn_app_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_turn_app_id ON public.chat_turn USING btree (app_id);


--
-- Name: idx_chat_turn_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_turn_created_at ON public.chat_turn USING btree (created_at);


--
-- Name: idx_chat_turn_integration_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_turn_integration_id ON public.chat_turn USING btree (integration_id);


--
-- Name: idx_chat_turn_llm_model_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_turn_llm_model_id ON public.chat_turn USING btree (llm_model_id);


--
-- Name: idx_chat_turn_session_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_turn_session_id ON public.chat_turn USING btree (session_id);


--
-- Name: idx_llm_model_enabled_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_llm_model_enabled_sort ON public.llm_model USING btree (enabled, sort_order);


--
-- Name: idx_llm_model_provider_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_llm_model_provider_id ON public.llm_model USING btree (provider_id);


--
-- Name: idx_llm_provider_config_enabled_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_llm_provider_config_enabled_sort ON public.llm_provider_config USING btree (enabled, sort_order);


--
-- Name: idx_user_context_field_enabled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_context_field_enabled ON public.user_context_field USING btree (enabled);


--
-- Name: uq_api_key_secret_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_api_key_secret_hash ON public.api_key USING btree (secret_hash);


--
-- Name: ai_app ai_app_llm_model_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_app
    ADD CONSTRAINT ai_app_llm_model_id_fkey FOREIGN KEY (llm_model_id) REFERENCES public.llm_model(id);


--
-- Name: ai_app_tool ai_app_tool_app_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_app_tool
    ADD CONSTRAINT ai_app_tool_app_id_fkey FOREIGN KEY (app_id) REFERENCES public.ai_app(id);


--
-- Name: ai_app_tool ai_app_tool_tool_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_app_tool
    ADD CONSTRAINT ai_app_tool_tool_id_fkey FOREIGN KEY (tool_id) REFERENCES public.ai_tool(id);


--
-- Name: api_key api_key_app_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_key
    ADD CONSTRAINT api_key_app_id_fkey FOREIGN KEY (app_id) REFERENCES public.ai_app(id) ON DELETE SET NULL;


--
-- Name: llm_model llm_model_provider_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.llm_model
    ADD CONSTRAINT llm_model_provider_id_fkey FOREIGN KEY (provider_id) REFERENCES public.llm_provider_config(id);


--
-- PostgreSQL database dump complete
--

\unrestrict q5cJibcziVQzrAVsPL0Jc67XH302DreOgqmQAzvsTNBkutvJtlvulQDPF7jxunF

