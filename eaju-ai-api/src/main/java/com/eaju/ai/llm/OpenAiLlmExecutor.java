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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.Closeable;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 基于库表配置的 OpenAI Chat Completions 调用（取代按品牌的 {@code *ChatModel} Bean）。
 */
@Component
public class OpenAiLlmExecutor {

    private final OpenAiChatCompletionClient httpClient;
    private final ChatCompletionResponseMapper responseMapper;
    private final ObjectMapper objectMapper;

    public OpenAiLlmExecutor(
            OpenAiChatCompletionClient httpClient,
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
        JsonNode root = httpClient.post(cfg.getBaseUrl(), cfg.getApiKey(), body);
        return responseMapper.map(cfg.getCode(), modelId, root);
    }

    /**
     * 流式请求：将上游 chunk 转发到 {@code emitter}；不在此调用 {@link SseEmitter#complete()}，由调用方在落库后结束。
     *
     * @param onEachChunkJson 每条上游 chunk 的 JSON 字符串，可为 null
     */
    public void chatStream(ChatRequestDto request, LlmProviderConfigSnapshot cfg, SseEmitter emitter,
                           Consumer<String> onEachChunkJson) throws Exception {
        chatStream(request, cfg, emitter, onEachChunkJson, null);
    }

    /**
     * @param upstreamHolder 非空时由 HTTP 客户端写入上游响应，便于在 SSE 完成/断开时关闭以停止拉流
     */
    public void chatStream(ChatRequestDto request, LlmProviderConfigSnapshot cfg, SseEmitter emitter,
                           Consumer<String> onEachChunkJson, AtomicReference<Closeable> upstreamHolder)
            throws Exception {
        cfg.validateOrThrow();
        String modelId = cfg.resolveUpstreamModelId(request.getModel(), request.getMode());
        ObjectNode body = buildChatCompletionBody(cfg, modelId, request, true);
        httpClient.postStream(cfg.getBaseUrl(), cfg.getApiKey(), body, emitter, onEachChunkJson, upstreamHolder);
    }

    private ObjectNode buildChatCompletionBody(LlmProviderConfigSnapshot cfg, String modelId,
                                               ChatRequestDto request, boolean streaming) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelId);
        if (streaming) {
            body.put("stream", true);
            // 告知上游在最后一个 chunk 中附带 usage，否则流式模式 token 统计全为 null
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
        InferenceDefaults d = cfg.defaultsOrEmpty();

        Double temperature = coalesce(request.getTemperature(), d.getTemperature());
        // 月之暗面：部分模型仅接受 temperature=1，否则会 400 invalid temperature
        if (isKimiProvider(cfg)) {
            body.put("temperature", 1.0);
        } else if (temperature != null) {
            body.put("temperature", temperature);
        }

        Integer maxTokens = coalesce(request.getMaxTokens(), d.getMaxTokens());
        if (maxTokens != null) {
            body.put("max_tokens", maxTokens);
        }

        Double topP = coalesce(request.getTopP(), d.getTopP());
        if (topP != null) {
            body.put("top_p", topP);
        }

        Integer topK = coalesce(request.getTopK(), d.getTopK());
        if (topK != null && topK > 0) {
            body.put("top_k", topK);
        }

        Double frequencyPenalty = coalesce(request.getFrequencyPenalty(), d.getFrequencyPenalty());
        if (frequencyPenalty != null) {
            body.put("frequency_penalty", frequencyPenalty);
        }

        Double presencePenalty = coalesce(request.getPresencePenalty(), d.getPresencePenalty());
        if (presencePenalty != null) {
            body.put("presence_penalty", presencePenalty);
        }

        Integer sampleCount = coalesce(request.getSampleCount(), d.getSampleCount());
        if (sampleCount != null && sampleCount > 0) {
            body.put("n", sampleCount);
        }

        List<String> stopDef = d.getStop();
        if (stopDef != null && !stopDef.isEmpty()) {
            ArrayNode stopArr = objectMapper.createArrayNode();
            for (String s : stopDef) {
                if (StringUtils.hasText(s)) {
                    stopArr.add(s.trim());
                }
            }
            if (stopArr.size() > 0) {
                body.set("stop", stopArr);
            }
        }

        ResponseFormatKind rf = coalesce(request.getResponseFormat(), d.getResponseFormat());
        if (rf == ResponseFormatKind.JSON_OBJECT) {
            ObjectNode rfNode = objectMapper.createObjectNode();
            rfNode.put("type", "json_object");
            body.set("response_format", rfNode);
        }
    }

    private void applyThinkingMode(LlmProviderConfigSnapshot cfg, ObjectNode body, ChatRequestDto request) {
        if (!cfg.supportsThinkingFlag()) {
            return;  // 该 provider 不支持 thinking API，不下发任何 thinking 参数
        }

        InferenceDefaults d = cfg.defaultsOrEmpty();
        // 优先取请求里明确传入的值，其次取提供方配置默认值
        Boolean explicit = request.getThinkingMode();
        Boolean effective = explicit != null ? explicit : d.getThinkingMode();

        boolean dashScope = cfg.usesDashScopeThinkingParam();

        // 用户/调用方明确关闭思考：无论 mode 配置如何，都必须显式告知上游禁用，
        // 否则像 Qwen3 这类默认开启思考的模型会仍然思考，导致等待延迟。
        if (Boolean.FALSE.equals(explicit)) {
            if (dashScope) {
                // DashScope/Qwen 格式：enable_thinking=false
                body.put("enable_thinking", false);
            } else {
                // DeepSeek/Anthropic 格式：thinking.type=disabled
                ObjectNode t = objectMapper.createObjectNode();
                t.put("type", "disabled");
                body.set("thinking", t);
            }
            return;
        }

        // 开启思考：要求 mode 配置声明支持 deepThinking，防止对不支持的模式误传
        if (!Boolean.TRUE.equals(effective)) {
            return;
        }
        String modeKey = StringUtils.hasText(request.getMode())
                ? request.getMode().trim()
                : cfg.getDefaultMode();
        if (!cfg.modeSupportsDeepThinking(modeKey)) {
            return;
        }
        if (dashScope) {
            body.put("enable_thinking", true);
        } else {
            ObjectNode t = objectMapper.createObjectNode();
            t.put("type", "enabled");
            body.set("thinking", t);
        }
    }

    private static boolean isKimiProvider(LlmProviderConfigSnapshot cfg) {
        return cfg.getCode() != null && "KIMI".equals(cfg.getCode().toUpperCase(Locale.ROOT));
    }

    private static <T> T coalesce(T requestVal, T defaultVal) {
        return requestVal != null ? requestVal : defaultVal;
    }

    private ArrayNode toMessagesArray(Iterable<ChatMessageDto> messages) {
        ArrayNode array = objectMapper.createArrayNode();
        for (ChatMessageDto m : messages) {
            if (m == null) {
                continue;
            }
            ObjectNode node = objectMapper.createObjectNode();
            node.put("role", m.getRole() != null ? m.getRole() : "");
            putMessageContentForUpstream(node, m);
            array.add(node);
        }
        return array;
    }

    /**
     * 无附件时 {@code content} 为字符串；有 {@code fileUrls} 时按 OpenAI 多段 content（text / image_url）组装。
     */
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
            if (!StringUtils.hasText(u)) {
                continue;
            }
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
        String low = path.toLowerCase(Locale.ROOT);
        return low.endsWith(".png")
                || low.endsWith(".jpg")
                || low.endsWith(".jpeg")
                || low.endsWith(".gif")
                || low.endsWith(".webp")
                || low.endsWith(".bmp");
    }
}
