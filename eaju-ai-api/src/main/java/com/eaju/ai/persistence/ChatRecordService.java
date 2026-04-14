package com.eaju.ai.persistence;

import com.eaju.ai.dto.ChatRequestDto;
import com.eaju.ai.dto.ChatResponseDto;
import com.eaju.ai.persistence.entity.ChatTurnEntity;
import com.eaju.ai.persistence.repository.ChatTurnRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 将阻塞式对话结果写入 PostgreSQL；失败只记日志，不影响接口返回。
 */
@Service
public class ChatRecordService {

    private static final Logger log = LoggerFactory.getLogger(ChatRecordService.class);

    private final ChatTurnRepository chatTurnRepository;
    private final ObjectMapper objectMapper;

    public ChatRecordService(ChatTurnRepository chatTurnRepository, ObjectMapper objectMapper) {
        this.chatTurnRepository = chatTurnRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void saveBlockingTurn(ChatRequestDto clientRequest,
                                 ChatRequestDto requestSentToModel,
                                 ChatResponseDto response,
                                 boolean streamMode) {
        try {
            ChatTurnEntity row = new ChatTurnEntity();
            row.setSessionId(trimToNull(clientRequest.getSessionId()));
            row.setUserId(trimToNull(clientRequest.getUserId()));
            row.setProvider(StringUtils.hasText(clientRequest.getProvider())
                    ? clientRequest.getProvider().trim()
                    : "");
            row.setModel(trimToNull(response.getModel()));
            row.setClientMessagesJson(writeJson(clientRequest.getMessages()));
            row.setRequestMessagesJson(writeJson(requestSentToModel.getMessages()));
            row.setAssistantContent(response.getContent());
            row.setReasoningContent(trimToNull(response.getReasoningContent()));
            row.setUpstreamMessageId(trimToNull(response.getId()));
            row.setFinishReason(trimToNull(response.getFinishReason()));
            fillUsage(row, response.getUsage());
            row.setStreamMode(streamMode);
            if (clientRequest.getInternalApiKeyId() != null) {
                row.setApiKeyId(clientRequest.getInternalApiKeyId());
            }
            chatTurnRepository.save(row);
        } catch (Exception ex) {
            log.warn("写入 chat_turn 失败: {}", ex.getMessage(), ex);
        }
    }

    private static void fillUsage(ChatTurnEntity row, JsonNode usage) {
        if (usage == null || usage.isNull()) {
            return;
        }
        JsonNode p = usage.get("prompt_tokens");
        if (p != null && p.isNumber()) {
            row.setPromptTokens(p.intValue());
        }
        JsonNode c = usage.get("completion_tokens");
        if (c != null && c.isNumber()) {
            row.setCompletionTokens(c.intValue());
        }
        JsonNode t = usage.get("total_tokens");
        if (t != null && t.isNumber()) {
            row.setTotalTokens(t.intValue());
        }
    }

    private String writeJson(Object o) throws JsonProcessingException {
        return objectMapper.writeValueAsString(o);
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
