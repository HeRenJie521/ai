package com.eaju.ai.service;

import com.eaju.ai.dto.ChatMessageDto;
import com.eaju.ai.dto.conversation.ConversationResponseDto;
import com.eaju.ai.persistence.entity.ChatConversationEntity;
import com.eaju.ai.persistence.entity.ChatTurnEntity;
import com.eaju.ai.persistence.entity.LlmModelEntity;
import com.eaju.ai.persistence.entity.LlmProviderConfigEntity;
import com.eaju.ai.persistence.repository.ChatConversationRepository;
import com.eaju.ai.persistence.repository.ChatTurnRepository;
import com.eaju.ai.persistence.repository.LlmModelRepository;
import com.eaju.ai.persistence.repository.LlmProviderConfigRepository;
import com.eaju.ai.session.ChatSessionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChatConversationService {

    private static final int TITLE_MAX = 48;
    private static final Logger log = LoggerFactory.getLogger(ChatConversationService.class);

    private final ChatConversationRepository conversationRepository;
    private final ChatTurnRepository chatTurnRepository;
    private final ChatSessionService chatSessionService;
    private final ObjectMapper objectMapper;
    private final LlmModelRepository llmModelRepository;
    private final LlmProviderConfigRepository llmProviderRepository;

    public ChatConversationService(
            ChatConversationRepository conversationRepository,
            ChatTurnRepository chatTurnRepository,
            ChatSessionService chatSessionService,
            ObjectMapper objectMapper,
            LlmModelRepository llmModelRepository,
            LlmProviderConfigRepository llmProviderRepository) {
        this.conversationRepository = conversationRepository;
        this.chatTurnRepository = chatTurnRepository;
        this.chatSessionService = chatSessionService;
        this.objectMapper = objectMapper;
        this.llmModelRepository = llmModelRepository;
        this.llmProviderRepository = llmProviderRepository;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> loadMessagesForUser(String userId, String sessionId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("参数无效");
        }
        String sid = sessionId.trim();
        if (!conversationRepository.findByUserIdAndSessionIdAndDeletedAtIsNull(userId.trim(), sid).isPresent()) {
            throw new IllegalArgumentException("会话不存在或无权访问");
        }
        return rebuildMessagesFromTurns(userId.trim(), sid);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> loadMessagesForApiKey(Long apiKeyId, String sessionId) {
        if (apiKeyId == null || !StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("参数无效");
        }
        Optional<ChatConversationEntity> c = conversationRepository.findByApiKeyIdAndSessionIdAndDeletedAtIsNull(apiKeyId, sessionId.trim());
        if (!c.isPresent()) {
            throw new IllegalArgumentException("会话不存在或无权访问");
        }
        return loadMessagesForUser(c.get().getUserId(), sessionId.trim());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> loadMessagesForAdmin(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("参数无效");
        }
        List<ChatTurnEntity> turns = chatTurnRepository.findBySessionIdOrderByCreatedAtAsc(sessionId.trim());
        if (turns.isEmpty()) return Collections.emptyList();
        List<ChatMessageDto> result = new ArrayList<>();
        for (ChatTurnEntity turn : turns) {
            String ts = turn.getCreatedAt() != null ? turn.getCreatedAt().toString() : null;
            List<ChatMessageDto> reqMsgs = parseRequestMessages(turn.getRequestMessagesJson());
            if (!reqMsgs.isEmpty()) {
                ChatMessageDto userMsg = reqMsgs.get(reqMsgs.size() - 1);
                if ("user".equals(userMsg.getRole())) {
                    userMsg.setCreatedAt(ts);
                    result.add(userMsg);
                }
            }
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

    private List<ChatMessageDto> rebuildMessagesFromTurns(String userId, String sessionId) {
        List<ChatTurnEntity> turns = chatTurnRepository.findBySessionIdAndUserIdOrderByCreatedAtAsc(sessionId, userId);
        if (turns.isEmpty()) return Collections.emptyList();
        List<ChatMessageDto> result = new ArrayList<>();
        for (ChatTurnEntity turn : turns) {
            String ts = turn.getCreatedAt() != null ? turn.getCreatedAt().toString() : null;
            List<ChatMessageDto> reqMsgs = parseRequestMessages(turn.getRequestMessagesJson());
            if (!reqMsgs.isEmpty()) {
                ChatMessageDto userMsg = reqMsgs.get(reqMsgs.size() - 1);
                if ("user".equals(userMsg.getRole())) {
                    userMsg.setCreatedAt(ts);
                    result.add(userMsg);
                }
            }
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

    private List<ChatMessageDto> parseRequestMessages(String json) {
        if (!StringUtils.hasText(json)) return new ArrayList<>();
        try {
            List<ChatMessageDto> parsed = objectMapper.readValue(json.trim(), new TypeReference<List<ChatMessageDto>>() {});
            if (parsed == null) return new ArrayList<>();
            for (ChatMessageDto m : parsed) {
                if (m != null && m.getContent() == null) m.setContent("");
            }
            return parsed;
        } catch (Exception ex) {
            log.warn("解析 request_messages_json 失败: {}", ex.getMessage());
            return new ArrayList<>();
        }
    }

    @Transactional(readOnly = true)
    public List<ConversationResponseDto> listForUser(String userId) {
        List<ConversationResponseDto> out = new ArrayList<>();
        if (!StringUtils.hasText(userId)) return out;
        // 返回用户自己的会话（含 CHAT 和应用来源），过滤掉 API Key 和 embed 来源
        for (ChatConversationEntity e : conversationRepository
                .findByUserIdAndApiKeyIdIsNullAndIntegrationIdIsNullAndDeletedAtIsNullOrderByLastMessageAtDesc(userId.trim())) {
            out.add(toDto(e));
        }
        return out;
    }

    /** 返回该用户所有未删除会话（含 app/embed/apiKey 类型），供 /home 页全量展示 */
    @Transactional(readOnly = true)
    public List<ConversationResponseDto> listAllForUser(String userId) {
        List<ConversationResponseDto> out = new ArrayList<>();
        if (!StringUtils.hasText(userId)) return out;
        for (ChatConversationEntity e : conversationRepository
                .findByUserIdAndDeletedAtIsNullOrderByLastMessageAtDesc(userId.trim())) {
            out.add(toDto(e));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<ConversationResponseDto> listForApiKey(Long apiKeyId) {
        List<ConversationResponseDto> out = new ArrayList<>();
        if (apiKeyId == null) return out;
        for (ChatConversationEntity e : conversationRepository.findByApiKeyIdAndDeletedAtIsNullOrderByLastMessageAtDesc(apiKeyId)) {
            out.add(toDto(e));
        }
        return out;
    }

    @Transactional
    public ConversationResponseDto createNew(String userId, Long apiKeyId) {
        return createNew(userId, apiKeyId, null);
    }

    @Transactional
    public ConversationResponseDto createNew(String userId, Long apiKeyId, Long appId) {
        if (!StringUtils.hasText(userId)) throw new IllegalArgumentException("用户无效");
        String sessionId = UUID.randomUUID().toString();
        ChatConversationEntity n = new ChatConversationEntity();
        n.setUserId(userId.trim());
        n.setSessionId(sessionId);
        n.setTitle("新对话");
        n.setLastMessageAt(Instant.now());
        if (apiKeyId != null) n.setApiKeyId(apiKeyId);
        if (appId != null) n.setAppId(appId);
        conversationRepository.save(n);
        return toDto(n);
    }

    @Transactional
    public void touchOnChatStart(String userId, Long apiKeyId, String sessionId, List<ChatMessageDto> messages,
                                 String providerCode, String modeKey) {
        touchOnChatStart(userId, apiKeyId, null, null, sessionId, messages, providerCode, modeKey);
    }

    @Transactional
    public void touchOnChatStart(String userId, Long apiKeyId, Long integrationId, String sessionId,
                                 List<ChatMessageDto> messages, String providerCode, String modeKey) {
        touchOnChatStart(userId, apiKeyId, integrationId, null, sessionId, messages, providerCode, modeKey);
    }

    @Transactional
    public void touchOnChatStart(String userId, Long apiKeyId, Long integrationId, Long appId, String sessionId,
                                 List<ChatMessageDto> messages, String providerCode, String modeKey) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(sessionId)) return;
        String uid = userId.trim();
        String sid = sessionId.trim();
        String titleHint = deriveTitleFromMessages(messages);

        // 解析 llmModelId 和 providerDisplayName
        Long llmModelId = null;
        String providerDisplayName = null;
        if (StringUtils.hasText(providerCode) && StringUtils.hasText(modeKey)) {
            llmModelId = llmModelRepository.findByProviderCodeAndName(providerCode.trim(), modeKey.trim())
                    .map(LlmModelEntity::getId)
                    .orElse(null);
        }
        if (StringUtils.hasText(providerCode)) {
            providerDisplayName = llmProviderRepository.findByCodeIgnoreCase(providerCode.trim())
                    .map(LlmProviderConfigEntity::getDisplayName)
                    .orElse(null);
        }

        Optional<ChatConversationEntity> opt = conversationRepository.findByUserIdAndSessionId(uid, sid);
        if (opt.isPresent()) {
            ChatConversationEntity e = opt.get();
            e.setLastMessageAt(Instant.now());
            if (apiKeyId != null) e.setApiKeyId(apiKeyId);
            if (integrationId != null) e.setIntegrationId(integrationId);
            if (appId != null) e.setAppId(appId);
            if (("新对话".equals(e.getTitle()) || !StringUtils.hasText(e.getTitle())) && StringUtils.hasText(titleHint)) {
                e.setTitle(titleHint);
            }
            applyLastModelChoice(e, providerCode, modeKey, llmModelId, providerDisplayName);
            conversationRepository.save(e);
        } else {
            ChatConversationEntity n = new ChatConversationEntity();
            n.setUserId(uid);
            n.setSessionId(sid);
            n.setTitle(StringUtils.hasText(titleHint) ? titleHint : "新对话");
            n.setLastMessageAt(Instant.now());
            if (apiKeyId != null) n.setApiKeyId(apiKeyId);
            if (integrationId != null) n.setIntegrationId(integrationId);
            if (appId != null) n.setAppId(appId);
            applyLastModelChoice(n, providerCode, modeKey, llmModelId, providerDisplayName);
            conversationRepository.save(n);
        }
    }

    private static void applyLastModelChoice(ChatConversationEntity e, String providerCode, String modeKey, Long llmModelId, String providerDisplayName) {
        if (StringUtils.hasText(providerCode)) e.setLastProviderCode(providerCode.trim());
        if (StringUtils.hasText(modeKey)) {
            String mk = modeKey.trim();
            if (mk.length() > 512) mk = mk.substring(0, 512);
            e.setLastModeKey(mk);
        }
        if (llmModelId != null) e.setLlmModelId(llmModelId);
        if (StringUtils.hasText(providerDisplayName)) e.setLastProviderDisplayName(providerDisplayName.trim());
    }

    @Transactional
    public void deleteForUser(String userId, String sessionId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("参数无效");
        }
        String uid = userId.trim();
        String sid = sessionId.trim();
        if (!conversationRepository.findByUserIdAndSessionIdAndDeletedAtIsNull(uid, sid).isPresent()) {
            throw new IllegalArgumentException("会话不存在或无权删除");
        }
        chatSessionService.deleteSession(sid);
        conversationRepository.softDeleteByUserIdAndSessionId(uid, sid, Instant.now());
    }

    @Transactional
    public void deleteForApiKey(Long apiKeyId, String sessionId) {
        if (apiKeyId == null || !StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("参数无效");
        }
        Optional<ChatConversationEntity> opt =
                conversationRepository.findByApiKeyIdAndSessionIdAndDeletedAtIsNull(apiKeyId, sessionId.trim());
        if (!opt.isPresent()) throw new IllegalArgumentException("会话不存在或无权删除");
        ChatConversationEntity e = opt.get();
        String sid = e.getSessionId().trim();
        chatSessionService.deleteSession(sid);
        conversationRepository.softDeleteByUserIdAndSessionId(e.getUserId(), sid, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<ConversationResponseDto> listForIntegration(Long integrationId, String userId) {
        List<ConversationResponseDto> out = new ArrayList<>();
        if (integrationId == null || !StringUtils.hasText(userId)) return out;
        for (ChatConversationEntity e :
                conversationRepository.findByIntegrationIdAndUserIdAndDeletedAtIsNullOrderByLastMessageAtDesc(integrationId, userId.trim())) {
            out.add(toDto(e));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<ConversationResponseDto> listForApp(Long appId, String userId) {
        List<ConversationResponseDto> out = new ArrayList<>();
        if (appId == null || !StringUtils.hasText(userId)) return out;
        for (ChatConversationEntity e :
                conversationRepository.findConversationsForAppUser(userId.trim(), appId)) {
            out.add(toDto(e));
        }
        return out;
    }

    @Transactional
    public void deleteForIntegration(Long integrationId, String sessionId) {
        if (integrationId == null || !StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("参数无效");
        }
        String sid = sessionId.trim();
        if (!conversationRepository.findByIntegrationIdAndSessionIdAndDeletedAtIsNull(integrationId, sid).isPresent()) {
            throw new IllegalArgumentException("会话不存在或无权删除");
        }
        chatSessionService.deleteSession(sid);
        conversationRepository.softDeleteByIntegrationIdAndSessionId(integrationId, sid, Instant.now());
    }

    private static String deriveTitleFromMessages(List<ChatMessageDto> messages) {
        if (messages == null || messages.isEmpty()) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessageDto m = messages.get(i);
            if (m == null || !StringUtils.hasText(m.getRole()) || !"user".equalsIgnoreCase(m.getRole().trim())) continue;
            String c = m.getContent();
            if (!StringUtils.hasText(c)) {
                if (m.getFileUrls() != null && !m.getFileUrls().isEmpty()) {
                    String u = m.getFileUrls().get(0);
                    if (StringUtils.hasText(u)) {
                        String t = ("[附件] " + u.trim()).replaceAll("\\s+", " ");
                        return t.length() > TITLE_MAX ? t.substring(0, TITLE_MAX) + "…" : t;
                    }
                }
                continue;
            }
            String t = c.trim().replaceAll("\\s+", " ");
            return t.length() > TITLE_MAX ? t.substring(0, TITLE_MAX) + "…" : t;
        }
        return null;
    }

    private ConversationResponseDto toDto(ChatConversationEntity e) {
        ConversationResponseDto dto = new ConversationResponseDto();
        dto.setSessionId(e.getSessionId());
        dto.setTitle(e.getTitle());
        dto.setLastMessageAt(e.getLastMessageAt() != null ? e.getLastMessageAt().toString() : null);
        dto.setLastProviderCode(e.getLastProviderCode());
        dto.setLastModeKey(e.getLastModeKey());
        dto.setLastModelDisplayName(resolveModelDisplayName(e.getLlmModelId(), e.getLastProviderCode(), e.getLastModeKey()));
        dto.setAppId(e.getAppId());
        return dto;
    }

    private String resolveModelDisplayName(Long llmModelId, String providerCode, String modeKey) {
        if (llmModelId != null) {
            LlmModelEntity model = llmModelRepository.findById(llmModelId).orElse(null);
            if (model != null) {
                LlmProviderConfigEntity provider = llmProviderRepository.findById(model.getProviderId()).orElse(null);
                String pName = provider != null ? provider.getDisplayName() : "";
                return pName + "·" + model.getName();
            }
        }
        // 回退：用 providerCode + modeKey 字符串拼接
        if (StringUtils.hasText(providerCode) && StringUtils.hasText(modeKey)) {
            return providerCode + "·" + modeKey;
        }
        return null;
    }
}
