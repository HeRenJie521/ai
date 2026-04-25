package com.eaju.ai.llm;

import com.eaju.ai.dto.ChatMessageDto;
import com.eaju.ai.dto.ChatRequestDto;
import com.eaju.ai.dto.ChatResponseDto;
import com.eaju.ai.dto.ResponseFormatKind;
import com.eaju.ai.llm.support.ChatCompletionResponseMapper;
import com.eaju.ai.llm.support.InferenceDefaults;
import com.eaju.ai.llm.support.OpenAiChatCompletionClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Component
public class OpenAiLlmExecutor {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmExecutor.class);

    private final OpenAiChatCompletionClient httpClient;
    private final ChatCompletionResponseMapper responseMapper;
    private final ObjectMapper objectMapper;

    public OpenAiLlmExecutor(OpenAiChatCompletionClient httpClient,
                              ChatCompletionResponseMapper responseMapper,
                              ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.responseMapper = responseMapper;
        this.objectMapper = objectMapper;
    }

    public ChatResponseDto chat(ChatRequestDto request, LlmProviderConfigSnapshot cfg) {
        cfg.validateOrThrow();
        String modelId = cfg.resolveUpstreamModelId(request.getModel(), request.getMode());
        ObjectNode body = buildChatCompletionBody(cfg, modelId, request, false);
        log.info("[LLM请求] provider={} model={}", cfg.getCode(), modelId);
        log.info("[LLM请求体] {}", body);
        JsonNode root = httpClient.post(cfg.getBaseUrl(), cfg.getApiKey(), body);
        log.info("[LLM响应] {}", root);
        return responseMapper.map(cfg.getCode(), modelId, root);
    }

    public ChatResponseDto chatWithTools(ChatRequestDto request, LlmProviderConfigSnapshot cfg,
                                         ArrayNode toolsArray) {
        cfg.validateOrThrow();
        String modelId = cfg.resolveUpstreamModelId(request.getModel(), request.getMode());
        ObjectNode body = buildChatCompletionBody(cfg, modelId, request, false);
        if (toolsArray != null && toolsArray.size() > 0) {
            body.set("tools", toolsArray);
            body.put("tool_choice", "auto");
        }
        log.info("[LLM请求-工具] provider={} model={}", cfg.getCode(), modelId);
        log.info("[LLM请求体-工具] {}", body);
        JsonNode root = httpClient.post(cfg.getBaseUrl(), cfg.getApiKey(), body);
        log.info("[LLM响应-工具] {}", root);
        return responseMapper.map(cfg.getCode(), modelId, root);
    }

    public void chatStream(ChatRequestDto request, LlmProviderConfigSnapshot cfg, SseEmitter emitter,
                           Consumer<String> onEachChunkJson) throws Exception {
        chatStream(request, cfg, emitter, onEachChunkJson, null);
    }

    public void chatStream(ChatRequestDto request, LlmProviderConfigSnapshot cfg, SseEmitter emitter,
                           Consumer<String> onEachChunkJson, AtomicReference<Closeable> upstreamHolder)
            throws Exception {
        cfg.validateOrThrow();
        String modelId = cfg.resolveUpstreamModelId(request.getModel(), request.getMode());
        ObjectNode body = buildChatCompletionBody(cfg, modelId, request, true);
        log.info("[LLM请求-流式] provider={} model={}", cfg.getCode(), modelId);
        // 使用带重试的流式方法，超时或网络错误时自动重试最多 3 次
        httpClient.postStreamWithRetry(cfg.getBaseUrl(), cfg.getApiKey(), body, emitter, onEachChunkJson, upstreamHolder);
    }

    private ObjectNode buildChatCompletionBody(LlmProviderConfigSnapshot cfg, String modelId,
                                               ChatRequestDto request, boolean streaming) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelId);
        if (streaming) {
            body.put("stream", true);
            ObjectNode streamOptions = objectMapper.createObjectNode();
            streamOptions.put("include_usage", true);
            body.set("stream_options", streamOptions);
        }
        body.set("messages", toMessagesArray(request.getMessages()));
        applyInferenceParameters(cfg, body, request);
        applyThinkingMode(cfg, body, request);
        return body;
    }

    private void applyInferenceParameters(LlmProviderConfigSnapshot cfg, ObjectNode body, ChatRequestDto request) {
        String modeKey = resolveEffectiveModeKey(cfg, request);
        InferenceDefaults d = cfg.getModeDefaults(modeKey);

        Double temperature = coalesce(request.getTemperature(), d.getTemperature());
        // 使用提供商配置的强制 temperature（如 Kimi 只接受 1.0）
        if (cfg.getForceTemperature() != null) {
            body.put("temperature", cfg.getForceTemperature());
        } else if (temperature != null) {
            body.put("temperature", temperature);
        }

        Integer maxTokens = coalesce(request.getMaxTokens(), d.getMaxTokens());
        if (maxTokens != null) body.put("max_tokens", maxTokens);

        Double topP = coalesce(request.getTopP(), d.getTopP());
        if (topP != null) body.put("top_p", topP);

        Integer topK = coalesce(request.getTopK(), d.getTopK());
        if (topK != null && topK > 0) body.put("top_k", topK);

        Double frequencyPenalty = coalesce(request.getFrequencyPenalty(), d.getFrequencyPenalty());
        if (frequencyPenalty != null) body.put("frequency_penalty", frequencyPenalty);

        Double presencePenalty = coalesce(request.getPresencePenalty(), d.getPresencePenalty());
        if (presencePenalty != null) body.put("presence_penalty", presencePenalty);

        Integer sampleCount = coalesce(request.getSampleCount(), d.getSampleCount());
        if (sampleCount != null && sampleCount > 0) body.put("n", sampleCount);

        List<String> stopDef = d.getStop();
        if (stopDef != null && !stopDef.isEmpty()) {
            ArrayNode stopArr = objectMapper.createArrayNode();
            for (String s : stopDef) {
                if (StringUtils.hasText(s)) stopArr.add(s.trim());
            }
            if (stopArr.size() > 0) body.set("stop", stopArr);
        }

        ResponseFormatKind rf = coalesce(request.getResponseFormat(), d.getResponseFormat());
        if (rf == ResponseFormatKind.JSON_OBJECT) {
            ObjectNode rfNode = objectMapper.createObjectNode();
            rfNode.put("type", "json_object");
            body.set("response_format", rfNode);
            // 某些提供商（如 Qwen）要求 JSON 模式时 system message 中必须包含 "json" 关键词
            if (cfg.isJsonModeSystemHint()) {
                ensureJsonMentionInSystemMessage(body);
            }
        }
    }

    private void ensureJsonMentionInSystemMessage(ObjectNode body) {
        JsonNode messagesNode = body.get("messages");
        if (messagesNode == null || !messagesNode.isArray()) return;
        for (JsonNode msg : messagesNode) {
            if ("system".equals(msg.path("role").asText())) {
                if (msg.path("content").asText("").toLowerCase().contains("json")) return;
            }
        }
        ObjectNode systemMsg = objectMapper.createObjectNode();
        systemMsg.put("role", "system");
        systemMsg.put("content", "请始终以 JSON 格式返回响应。");
        ((ArrayNode) messagesNode).insert(0, systemMsg);
    }

    private void applyThinkingMode(LlmProviderConfigSnapshot cfg, ObjectNode body, ChatRequestDto request) {
        String modeKey = resolveEffectiveModeKey(cfg, request);

        if (!cfg.modeSupportsThinkingApi(modeKey)) {
            return;
        }

        InferenceDefaults d = cfg.getModeDefaults(modeKey);
        Boolean explicit = request.getThinkingMode();
        Boolean effective = explicit != null ? explicit : d.getThinkingMode();

        boolean dashScope = cfg.usesDashScopeThinkingParam();
        boolean forceThinking = cfg.modeForceThinkingEnabled(modeKey);

        if (Boolean.FALSE.equals(explicit)) {
            if (forceThinking) {
                // 强制开启思考的模型不允许关闭
                body.put("enable_thinking", true);
            } else if (dashScope) {
                body.put("enable_thinking", false);
            } else {
                ObjectNode t = objectMapper.createObjectNode();
                t.put("type", "disabled");
                body.set("thinking", t);
            }
            return;
        }

        if (!Boolean.TRUE.equals(effective) && !forceThinking) return;
        if (!cfg.modeSupportsDeepThinking(modeKey) && !forceThinking) return;

        if (forceThinking || Boolean.TRUE.equals(effective)) {
            if (dashScope) {
                body.put("enable_thinking", true);
            } else {
                ObjectNode t = objectMapper.createObjectNode();
                t.put("type", "enabled");
                body.set("thinking", t);
            }
        }
    }

    private static String resolveEffectiveModeKey(LlmProviderConfigSnapshot cfg, ChatRequestDto request) {
        if (request != null && StringUtils.hasText(request.getMode())) {
            return request.getMode().trim();
        }
        return cfg.getDefaultMode() != null ? cfg.getDefaultMode() : "";
    }

    private static <T> T coalesce(T requestVal, T defaultVal) {
        return requestVal != null ? requestVal : defaultVal;
    }

    private ArrayNode toMessagesArray(Iterable<ChatMessageDto> messages) {
        ArrayNode array = objectMapper.createArrayNode();
        for (ChatMessageDto m : messages) {
            if (m == null) continue;
            String role = m.getRole() != null ? m.getRole() : "";
            ObjectNode node = objectMapper.createObjectNode();
            node.put("role", role);
            if ("tool".equals(role)) {
                node.put("content", m.getContent() != null ? m.getContent() : "");
                if (StringUtils.hasText(m.getToolCallId())) {
                    node.put("tool_call_id", m.getToolCallId());
                }
            } else if ("assistant".equals(role) && StringUtils.hasText(m.getToolCallsJson())) {
                node.put("content", m.getContent() != null ? m.getContent() : "");
                try {
                    JsonNode toolCallsNode = objectMapper.readTree(m.getToolCallsJson());
                    // 剥掉 "index" 字段：该字段仅用于流式分块重组，发回给模型时会导致部分模型报错
                    ArrayNode cleanedToolCalls = objectMapper.createArrayNode();
                    if (toolCallsNode.isArray()) {
                        for (JsonNode tc : toolCallsNode) {
                            ObjectNode cleanTc = tc.deepCopy();
                            cleanTc.remove("index");
                            cleanedToolCalls.add(cleanTc);
                        }
                    }
                    node.set("tool_calls", cleanedToolCalls);
                } catch (Exception ex) {
                    // 解析失败，忽略 tool_calls
                }
            } else {
                putMessageContentForUpstream(node, m);
            }
            array.add(node);
        }
        return array;
    }

    private void putMessageContentForUpstream(ObjectNode node, ChatMessageDto m) {
        List<String> urls = m.getFileUrls();
        boolean hasUrls = urls != null && !urls.isEmpty();
        String rawText = m.getContent() != null ? m.getContent() : "";
        boolean hasText = StringUtils.hasText(rawText.trim());

        if (!hasUrls) {
            node.put("content", hasText ? rawText : "");
            return;
        }

        ArrayNode parts = objectMapper.createArrayNode();
        if (hasText) {
            ObjectNode textPart = objectMapper.createObjectNode();
            textPart.put("type", "text");
            textPart.put("text", rawText.trim());
            parts.add(textPart);
        }
        for (String u : urls) {
            if (!StringUtils.hasText(u)) continue;
            String url = u.trim();
            if (looksLikeImageUrl(url)) {
                ObjectNode imgPart = objectMapper.createObjectNode();
                imgPart.put("type", "image_url");
                ObjectNode imageUrl = objectMapper.createObjectNode();
                imageUrl.put("url", url);
                imgPart.set("image_url", imageUrl);
                parts.add(imgPart);
            } else {
                ObjectNode textPart = objectMapper.createObjectNode();
                textPart.put("type", "text");
                textPart.put("text", "[附件]\n" + url);
                parts.add(textPart);
            }
        }
        if (parts.size() == 0) {
            node.put("content", "");
            return;
        }
        node.set("content", parts);
    }

    private static boolean looksLikeImageUrl(String url) {
        int q = url.indexOf('?');
        String path = q > 0 ? url.substring(0, q) : url;
        String low = path.toLowerCase(java.util.Locale.ROOT);
        return low.endsWith(".png") || low.endsWith(".jpg") || low.endsWith(".jpeg")
                || low.endsWith(".gif") || low.endsWith(".webp") || low.endsWith(".bmp");
    }
}
