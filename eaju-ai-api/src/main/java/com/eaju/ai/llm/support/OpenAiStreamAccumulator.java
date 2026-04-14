package com.eaju.ai.llm.support;

import com.eaju.ai.dto.ChatResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * 解析 OpenAI 兼容 SSE chunk JSON，拼接正文与思考过程，供流式结束后写入 Redis / chat_turn。
 */
public class OpenAiStreamAccumulator {

    private final ObjectMapper objectMapper;
    private final boolean captureReasoning;
    private final StringBuilder content = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private String id;
    private String model;
    private String finishReason;
    private JsonNode usage;

    public OpenAiStreamAccumulator(ObjectMapper objectMapper, boolean captureReasoning) {
        this.objectMapper = objectMapper;
        this.captureReasoning = captureReasoning;
    }

    public void acceptChunkJson(@Nullable String chunkJson) {
        if (!StringUtils.hasText(chunkJson)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(chunkJson);
            if (root.hasNonNull("id")) {
                id = root.get("id").asText();
            }
            if (root.hasNonNull("model")) {
                model = root.get("model").asText();
            }
            if (root.has("usage") && !root.get("usage").isNull()) {
                usage = root.get("usage");
            }
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.size() == 0) {
                return;
            }
            JsonNode first = choices.get(0);
            if (first.hasNonNull("finish_reason")) {
                finishReason = first.get("finish_reason").asText();
            }
            JsonNode delta = first.get("delta");
            if (delta == null || !delta.isObject()) {
                return;
            }
            appendDeltaText(delta, "content", content);
            if (captureReasoning) {
                appendDeltaText(delta, "reasoning_content", reasoning);
            }
        } catch (Exception ignored) {
        }
    }

    private static void appendDeltaText(JsonNode delta, String field, StringBuilder target) {
        JsonNode n = delta.get(field);
        if (n != null && n.isTextual()) {
            target.append(n.asText());
        }
    }

    public boolean hasAssistantPayload() {
        return content.length() > 0 || reasoning.length() > 0;
    }

    public ChatResponseDto toResponse(String providerCode, String fallbackModel) {
        ChatResponseDto dto = new ChatResponseDto();
        dto.setProvider(providerCode);
        dto.setModel(StringUtils.hasText(model) ? model : fallbackModel);
        dto.setId(id);
        dto.setContent(content.toString());
        dto.setReasoningContent(reasoning.length() > 0 ? reasoning.toString() : null);
        dto.setFinishReason(finishReason);
        dto.setUsage(usage);
        return dto;
    }
}
