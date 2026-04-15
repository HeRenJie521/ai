package com.eaju.ai.web;

import com.eaju.ai.dto.ChatMessageDto;
import com.eaju.ai.dto.admin.ConversationAdminDto;
import com.eaju.ai.dto.admin.ConversationDetailDto;
import com.eaju.ai.dto.admin.ModelUsageRowDto;
import com.eaju.ai.dto.conversation.ConversationResponseDto;
import com.eaju.ai.persistence.entity.ApiKeyEntity;
import com.eaju.ai.persistence.entity.ChatConversationEntity;
import com.eaju.ai.persistence.entity.ChatTurnEntity;
import com.eaju.ai.persistence.repository.ApiKeyRepository;
import com.eaju.ai.persistence.repository.ChatConversationRepository;
import com.eaju.ai.persistence.repository.ChatTurnRepository;
import com.eaju.ai.service.ChatConversationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/conversations")
@Validated
public class AdminConversationController {

    private final ChatConversationRepository conversationRepository;
    private final ChatTurnRepository chatTurnRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ChatConversationService chatConversationService;
    private final ObjectMapper objectMapper;

    public AdminConversationController(
            ChatConversationRepository conversationRepository,
            ChatTurnRepository chatTurnRepository,
            ApiKeyRepository apiKeyRepository,
            ChatConversationService chatConversationService,
            ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.chatTurnRepository = chatTurnRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.chatConversationService = chatConversationService;
        this.objectMapper = objectMapper;
    }

    /**
     * 会话列表查询
     *
     * @param page     页码，从 0 开始
     * @param size     每页数量
     * @param userId   按手机号精确查询
     * @param apiKeyId 按 API Key ID 查询
     */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) Long apiKeyId) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastMessageAt"));
        Page<ChatConversationEntity> pageResult;

        if (userId != null && !userId.trim().isEmpty()) {
            pageResult = conversationRepository.findByUserIdOrderByLastMessageAtDesc(userId.trim(), pageable);
        } else if (apiKeyId != null) {
            pageResult = conversationRepository.findByApiKeyIdOrderByLastMessageAtDesc(apiKeyId, pageable);
        } else {
            pageResult = conversationRepository.findAllByOrderByLastMessageAtDesc(pageable);
        }

        // 预加载 API Key / 嵌入集成名称
        List<Long> relatedIds = pageResult.getContent().stream()
                .map(c -> c.getIntegrationId() != null ? c.getIntegrationId() : c.getApiKeyId())
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> apiKeyNameMap = new HashMap<>();
        for (Long akId : relatedIds) {
            apiKeyRepository.findById(akId).ifPresent(ak -> apiKeyNameMap.put(akId, ak.getName()));
        }

        List<ConversationAdminDto> list = new ArrayList<>();
        for (ChatConversationEntity e : pageResult.getContent()) {
            ConversationAdminDto dto = toAdminDto(e, apiKeyNameMap);
            // 统计 turn 数量和 token
            long turnCount = chatTurnRepository.findBySessionIdOrderByCreatedAtAsc(e.getSessionId()).size();
            List<Object[]> tokenStats = chatTurnRepository.sumTokensBySessionId(e.getSessionId());
            if (!tokenStats.isEmpty() && tokenStats.get(0) != null) {
                Object[] stats = tokenStats.get(0);
                dto.setTurnCount(turnCount);
                dto.setTotalTokens(((Number) stats[2]).longValue());
            }
            list.add(dto);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("content", list);
        result.put("totalElements", pageResult.getTotalElements());
        result.put("totalPages", pageResult.getTotalPages());
        result.put("page", pageResult.getNumber());
        result.put("size", pageResult.getSize());
        return result;
    }

    /**
     * 会话详情
     */
    @GetMapping("/{sessionId}")
    public ConversationDetailDto detail(@PathVariable String sessionId) {
        ChatConversationEntity e = conversationRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));

        ConversationDetailDto dto = new ConversationDetailDto();
        dto.setSessionId(e.getSessionId());
        dto.setUserId(e.getUserId());
        dto.setTitle(e.getTitle());
        dto.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        dto.setLastMessageAt(e.getLastMessageAt() != null ? e.getLastMessageAt().toString() : null);
        dto.setLastProviderCode(e.getLastProviderCode());
        dto.setLastModeKey(e.getLastModeKey());
        dto.setApiKeyId(e.getApiKeyId());
        dto.setDeletedAt(e.getDeletedAt() != null ? e.getDeletedAt().toString() : null);

        if (e.getIntegrationId() != null) {
            apiKeyRepository.findById(e.getIntegrationId())
                    .ifPresent(ak -> dto.setApiKeyName(ak.getName()));
            dto.setType("EMBED");
        } else if (e.getApiKeyId() != null) {
            apiKeyRepository.findById(e.getApiKeyId())
                    .ifPresent(ak -> dto.setApiKeyName(ak.getName()));
            dto.setType("API_KEY");
        } else {
            dto.setType("CHAT");
        }

        // Token 用量
        List<Object[]> tokenStats = chatTurnRepository.sumTokensBySessionId(sessionId);
        if (!tokenStats.isEmpty() && tokenStats.get(0) != null) {
            Object[] stats = tokenStats.get(0);
            ConversationDetailDto.TokenUsageDto usage = new ConversationDetailDto.TokenUsageDto();
            usage.setPromptTokens(((Number) stats[0]).longValue());
            usage.setCompletionTokens(((Number) stats[1]).longValue());
            usage.setTotalTokens(((Number) stats[2]).longValue());
            dto.setUsage(usage);
        }

        // 各模型用量
        List<Object[]> modelStats = chatTurnRepository.aggregateBySessionId(sessionId);
        List<ModelUsageRowDto> modelUsage = new ArrayList<>();
        for (Object[] stats : modelStats) {
            ModelUsageRowDto mu = new ModelUsageRowDto();
            mu.setModel((String) stats[0]);
            mu.setTurnCount(((Number) stats[1]).longValue());
            mu.setTotalTokens(((Number) stats[4]).longValue());
            modelUsage.add(mu);
        }
        dto.setByModel(modelUsage);

        return dto;
    }

    /**
     * 获取会话的消息历史
     */
    @GetMapping("/{sessionId}/messages")
    public List<ChatMessageDto> messages(@PathVariable String sessionId) {
        // 管理员可查看任意会话（包括已逻辑删除的），直接按 sessionId 读取
        conversationRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        return chatConversationService.loadMessagesForAdmin(sessionId);
    }

    private ConversationAdminDto toAdminDto(ChatConversationEntity e, Map<Long, String> apiKeyNameMap) {
        ConversationAdminDto dto = new ConversationAdminDto();
        dto.setId(e.getId());
        dto.setSessionId(e.getSessionId());
        dto.setUserId(e.getUserId());
        dto.setTitle(e.getTitle());
        dto.setLastMessageAt(e.getLastMessageAt() != null ? e.getLastMessageAt().toString() : null);
        dto.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        dto.setLastProviderCode(e.getLastProviderCode());
        dto.setLastModeKey(e.getLastModeKey());
        dto.setApiKeyId(e.getApiKeyId());
        if (e.getIntegrationId() != null) {
            dto.setApiKeyName(apiKeyNameMap.get(e.getIntegrationId()));
            dto.setType("EMBED");
        } else if (e.getApiKeyId() != null) {
            dto.setApiKeyName(apiKeyNameMap.get(e.getApiKeyId()));
            dto.setType("API_KEY");
        } else {
            dto.setType("CHAT");
        }
        dto.setDeletedAt(e.getDeletedAt() != null ? e.getDeletedAt().toString() : null);
        return dto;
    }
}