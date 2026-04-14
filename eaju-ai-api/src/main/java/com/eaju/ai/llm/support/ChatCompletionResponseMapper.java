package com.eaju.ai.llm.support;

import com.eaju.ai.dto.ChatResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class ChatCompletionResponseMapper {

    public ChatResponseDto map(String providerCode, String model, JsonNode root) {
        ChatResponseDto dto = new ChatResponseDto();
        dto.setProvider(providerCode);
        dto.setModel(model);
        if (root != null) {
            if (root.hasNonNull("id")) {
                dto.setId(root.get("id").asText());
            }
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode first = choices.get(0);
                if (first.hasNonNull("finish_reason")) {
                    dto.setFinishReason(first.get("finish_reason").asText());
                }
                JsonNode message = first.get("message");
                if (message != null) {
                    if (message.hasNonNull("content")) {
                        JsonNode c = message.get("content");
                        if (c.isTextual()) {
                            dto.setContent(c.asText());
                        } else if (!c.isNull()) {
                            dto.setContent(c.toString());
                        }
                    }
                    if (message.hasNonNull("reasoning_content")) {
                        dto.setReasoningContent(message.get("reasoning_content").asText());
                    }
                }
            }
            if (root.has("usage")) {
                dto.setUsage(root.get("usage"));
            }
            dto.setRaw(root);
        }
        return dto;
    }
}
