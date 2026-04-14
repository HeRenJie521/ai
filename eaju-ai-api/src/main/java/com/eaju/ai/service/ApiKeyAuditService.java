package com.eaju.ai.service;

import com.eaju.ai.dto.ChatMessageDto;
import com.eaju.ai.dto.admin.ApiKeyUsageDto;
import com.eaju.ai.dto.admin.ModelUsageRowDto;
import com.eaju.ai.dto.admin.RecentTurnDto;
import com.eaju.ai.persistence.entity.ChatConversationEntity;
import com.eaju.ai.persistence.entity.ChatTurnEntity;
import com.eaju.ai.persistence.repository.ApiKeyRepository;
import com.eaju.ai.persistence.repository.ChatConversationRepository;
import com.eaju.ai.persistence.repository.ChatTurnRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class ApiKeyAuditService {

    private static final int PREVIEW_MAX = 240;

    private final ApiKeyRepository apiKeyRepository;
    private final ChatTurnRepository chatTurnRepository;
    private final ChatConversationRepository conversationRepository;
    private final ObjectMapper objectMapper;

    public ApiKeyAuditService(ApiKeyRepository apiKeyRepository,
                              ChatTurnRepository chatTurnRepository,
                              ChatConversationRepository conversationRepository,
                              ObjectMapper objectMapper) {
        this.apiKeyRepository = apiKeyRepository;
        this.chatTurnRepository = chatTurnRepository;
        this.conversationRepository = conversationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ApiKeyUsageDto buildUsage(Long apiKeyId) {
        com.eaju.ai.persistence.entity.ApiKeyEntity entity = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new IllegalArgumentException("集成不存在"));

        ApiKeyUsageDto dto = new ApiKeyUsageDto();
        boolean isEmbed = entity.getType() == 2;

        if (isEmbed) {
            // WEB_EMBED：按 integration_id 统计
            dto.setTurnCount(chatTurnRepository.countByIntegrationId(apiKeyId));
            dto.setTotalPromptTokens(chatTurnRepository.sumPromptTokensByIntegrationId(apiKeyId));
            dto.setTotalCompletionTokens(chatTurnRepository.sumCompletionTokensByIntegrationId(apiKeyId));
            dto.setTotalTokens(chatTurnRepository.sumTotalTokensByIntegrationId(apiKeyId));

            List<ModelUsageRowDto> byModel = new ArrayList<ModelUsageRowDto>();
            for (Object[] row : chatTurnRepository.aggregateByModelForIntegration(apiKeyId)) {
                ModelUsageRowDto m = new ModelUsageRowDto();
                m.setModel(row[0] != null ? row[0].toString() : "");
                m.setTurnCount(toLong(row[1]));
                m.setTotalTokens(toLong(row[2]));
                byModel.add(m);
            }
            dto.setByModel(byModel);

            List<RecentTurnDto> recent = new ArrayList<RecentTurnDto>();
            for (ChatTurnEntity t : chatTurnRepository.findTop50ByIntegrationIdOrderByCreatedAtDesc(apiKeyId)) {
                recent.add(toRecentTurnDto(t));
            }
            dto.setRecentTurns(recent);
        } else {
            // API_KEY：按 api_key_id 统计（原有逻辑）
            dto.setTurnCount(chatTurnRepository.countByApiKeyId(apiKeyId));
            dto.setTotalPromptTokens(chatTurnRepository.sumPromptTokensByApiKeyId(apiKeyId));
            dto.setTotalCompletionTokens(chatTurnRepository.sumCompletionTokensByApiKeyId(apiKeyId));
            dto.setTotalTokens(chatTurnRepository.sumTotalTokensByApiKeyId(apiKeyId));

            List<ModelUsageRowDto> byModel = new ArrayList<ModelUsageRowDto>();
            for (Object[] row : chatTurnRepository.aggregateByModel(apiKeyId)) {
                ModelUsageRowDto m = new ModelUsageRowDto();
                m.setModel(row[0] != null ? row[0].toString() : "");
                m.setTurnCount(toLong(row[1]));
                m.setTotalTokens(toLong(row[2]));
                byModel.add(m);
            }
            dto.setByModel(byModel);

            List<RecentTurnDto> recent = new ArrayList<RecentTurnDto>();
            for (ChatTurnEntity t : chatTurnRepository.findTop50ByApiKeyIdOrderByCreatedAtDesc(apiKeyId)) {
                recent.add(toRecentTurnDto(t));
            }
            dto.setRecentTurns(recent);
        }
        return dto;
    }

    private static RecentTurnDto toRecentTurnDto(ChatTurnEntity t) {
        RecentTurnDto r = new RecentTurnDto();
        r.setId(t.getId());
        r.setSessionId(t.getSessionId());
        r.setUserId(t.getUserId());
        r.setProvider(t.getProvider());
        r.setModel(t.getModel());
        r.setPromptTokens(t.getPromptTokens());
        r.setCompletionTokens(t.getCompletionTokens());
        r.setTotalTokens(t.getTotalTokens());
        r.setCreatedAt(t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
        r.setAssistantPreview(preview(t.getAssistantContent()));
        return r;
    }

    /**
     * 按轮次还原会话消息并附带时间戳，专供管理端消息查看。
     * 每轮取 requestMessages 的最后一条（本轮新增用户消息）+ 助手回复，均带 createdAt。
     */
    @Transactional(readOnly = true)
    public List<ChatMessageDto> loadMessagesWithTimestamps(Long integrationRecordId, String sessionId) {
        if (integrationRecordId == null || !StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("参数无效");
        }
        com.eaju.ai.persistence.entity.ApiKeyEntity entity = apiKeyRepository.findById(integrationRecordId)
                .orElseThrow(() -> new IllegalArgumentException("集成不存在"));
        String sid = sessionId.trim();
        Optional<ChatConversationEntity> conv;
        if (entity.getType() == 2) {
            // WEB_EMBED：会话按 integration_id 关联
            conv = conversationRepository.findByIntegrationIdAndSessionIdAndDeletedAtIsNull(integrationRecordId, sid);
        } else {
            // API_KEY：会话按 api_key_id 关联
            conv = conversationRepository.findByApiKeyIdAndSessionIdAndDeletedAtIsNull(integrationRecordId, sid);
        }
        if (!conv.isPresent()) {
            throw new IllegalArgumentException("会话不存在或无权访问");
        }
        String userId = conv.get().getUserId();
        List<ChatTurnEntity> turns =
                chatTurnRepository.findBySessionIdAndUserIdOrderByCreatedAtAsc(sessionId.trim(), userId);
        if (turns.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChatMessageDto> result = new ArrayList<ChatMessageDto>();
        for (ChatTurnEntity turn : turns) {
            String ts = turn.getCreatedAt() != null ? turn.getCreatedAt().toString() : null;
            // 取本轮 requestMessages 最后一条（即本轮新增的用户消息）
            List<ChatMessageDto> reqMsgs = parseMessages(turn.getRequestMessagesJson());
            if (!reqMsgs.isEmpty()) {
                ChatMessageDto userMsg = reqMsgs.get(reqMsgs.size() - 1);
                if ("user".equals(userMsg.getRole())) {
                    userMsg.setCreatedAt(ts);
                    result.add(userMsg);
                }
            }
            // 助手回复
            ChatMessageDto assistant = new ChatMessageDto();
            assistant.setRole("assistant");
            assistant.setContent(StringUtils.hasText(turn.getAssistantContent()) ? turn.getAssistantContent() : "");
            if (StringUtils.hasText(turn.getReasoningContent())) {
                assistant.setReasoningContent(turn.getReasoningContent());
            }
            assistant.setCreatedAt(ts);
            result.add(assistant);
        }
        return result;
    }

    private List<ChatMessageDto> parseMessages(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<ChatMessageDto>();
        }
        try {
            List<ChatMessageDto> parsed = objectMapper.readValue(
                    json.trim(), new TypeReference<List<ChatMessageDto>>() {});
            return parsed != null ? parsed : new ArrayList<ChatMessageDto>();
        } catch (Exception ex) {
            return new ArrayList<ChatMessageDto>();
        }
    }

    private static String preview(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String s = content.trim().replaceAll("\\s+", " ");
        if (s.length() <= PREVIEW_MAX) {
            return s;
        }
        return s.substring(0, PREVIEW_MAX) + "…";
    }

    private static long toLong(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
