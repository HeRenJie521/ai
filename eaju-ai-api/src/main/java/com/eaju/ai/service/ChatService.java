package com.eaju.ai.service;

import com.eaju.ai.dto.ChatMessageDto;
import com.eaju.ai.dto.ChatRequestDto;
import com.eaju.ai.dto.ChatResponseDto;
import com.eaju.ai.llm.LlmProviderConfigSnapshot;
import com.eaju.ai.llm.OpenAiLlmExecutor;
import com.eaju.ai.llm.support.OpenAiStreamAccumulator;
import com.eaju.ai.persistence.ChatRecordService;
import com.eaju.ai.persistence.entity.AiAppEntity;
import com.eaju.ai.persistence.entity.ApiKeyEntity;
import com.eaju.ai.persistence.repository.AiAppRepository;
import com.eaju.ai.persistence.repository.ApiKeyRepository;
import com.eaju.ai.session.ChatSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ChatService {

    private static final long STREAM_TIMEOUT_MS = 30L * 60L * 1000L;

    private final LlmProviderConfigService llmProviderConfigService;
    private final OpenAiLlmExecutor openAiLlmExecutor;
    private final ExecutorService chatStreamExecutor;
    private final ChatSessionService chatSessionService;
    private final ChatRecordService chatRecordService;
    private final ObjectMapper objectMapper;
    private final ApiKeyRepository apiKeyRepository;
    private final AiAppRepository aiAppRepository;

    public ChatService(LlmProviderConfigService llmProviderConfigService,
                       OpenAiLlmExecutor openAiLlmExecutor,
                       @Qualifier("chatStreamExecutor") ExecutorService chatStreamExecutor,
                       ChatSessionService chatSessionService,
                       ChatRecordService chatRecordService,
                       ObjectMapper objectMapper,
                       ApiKeyRepository apiKeyRepository,
                       AiAppRepository aiAppRepository) {
        this.llmProviderConfigService = llmProviderConfigService;
        this.openAiLlmExecutor = openAiLlmExecutor;
        this.chatStreamExecutor = chatStreamExecutor;
        this.chatSessionService = chatSessionService;
        this.chatRecordService = chatRecordService;
        this.objectMapper = objectMapper;
        this.apiKeyRepository = apiKeyRepository;
        this.aiAppRepository = aiAppRepository;
    }

    public ChatResponseDto chat(ChatRequestDto request) {
        ChatRequestDto effective = chatSessionService.mergeHistory(request);
        ChatRequestDto forLlm = withSystemPrompt(request, effective);
        LlmProviderConfigSnapshot cfg = llmProviderConfigService.requireSnapshot(forLlm.getProvider());
        ChatResponseDto response = openAiLlmExecutor.chat(forLlm, cfg);
        chatSessionService.appendAssistantToSession(request, effective, response);
        chatRecordService.saveBlockingTurn(request, effective, response, false);
        if (!cfg.resolveThinkingContentWanted(forLlm)) {
            response.setReasoningContent(null);
        }
        return response;
    }

    public SseEmitter chatStream(ChatRequestDto request) {
        ChatRequestDto effective = chatSessionService.mergeHistory(request);
        ChatRequestDto forLlm = withSystemPrompt(request, effective);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        AtomicReference<Closeable> upstreamHolder = new AtomicReference<>();
        emitter.onCompletion(() -> {
            Closeable c = upstreamHolder.getAndSet(null);
            if (c != null) {
                try {
                    c.close();
                } catch (IOException ignored) {
                }
            }
        });
        chatStreamExecutor.execute(() -> {
            try {
                LlmProviderConfigSnapshot cfg = llmProviderConfigService.requireSnapshot(forLlm.getProvider());
                OpenAiStreamAccumulator accum = new OpenAiStreamAccumulator(objectMapper, true);
                String resolvedModel = cfg.resolveUpstreamModelId(forLlm.getModel(), forLlm.getMode());
                openAiLlmExecutor.chatStream(forLlm, cfg, emitter, accum::acceptChunkJson, upstreamHolder);
                if (accum.hasAssistantPayload()) {
                    ChatResponseDto response = accum.toResponse(cfg.getCode(), resolvedModel);
                    chatSessionService.appendAssistantToSession(request, effective, response);
                    chatRecordService.saveBlockingTurn(request, effective, response, true);
                }
            } catch (Exception ex) {
                try {
                    emitter.completeWithError(ex);
                } catch (Exception ignored) {
                }
                return;
            }
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        });
        return emitter;
    }

    /**
     * 若当前请求来自集成且关联了 AI 应用，则将 system message 注入到发给 LLM 的消息列表首位。
     * 通过 integration.app_id → ai_app 加载系统提示词。
     * 返回的新 DTO 仅用于调用 LLM，不会写入 Redis 会话历史，确保每轮请求都带最新配置。
     */
    private ChatRequestDto withSystemPrompt(ChatRequestDto original, ChatRequestDto effective) {
        Long integrationId = original.getInternalIntegrationId();
        if (integrationId == null) {
            return effective;
        }
        java.util.Optional<ApiKeyEntity> integrationOpt = apiKeyRepository.findByIdAndDeletedIsFalse(integrationId);
        if (!integrationOpt.isPresent()) {
            return effective;
        }
        ApiKeyEntity integration = integrationOpt.get();
        if (integration.getAppId() == null) {
            return effective;
        }
        java.util.Optional<AiAppEntity> appOpt = aiAppRepository.findByIdAndDeletedIsFalse(integration.getAppId());
        if (!appOpt.isPresent()) {
            return effective;
        }
        AiAppEntity app = appOpt.get();
        String systemContent = buildSystemContent(app);
        if (!StringUtils.hasText(systemContent)) {
            return effective;
        }
        ChatMessageDto systemMsg = new ChatMessageDto();
        systemMsg.setRole("system");
        systemMsg.setContent(systemContent);

        List<ChatMessageDto> messages = new ArrayList<>();
        messages.add(systemMsg);
        messages.addAll(effective.getMessages());

        ChatRequestDto forLlm = new ChatRequestDto();
        BeanUtils.copyProperties(effective, forLlm, "messages");
        forLlm.setMessages(messages);
        return forLlm;
    }

    private static String buildSystemContent(AiAppEntity app) {
        StringBuilder sb = new StringBuilder();
        String role = app.getSystemRole();
        String task = app.getSystemTask();
        String constraints = app.getSystemConstraints();
        if (StringUtils.hasText(role)) {
            sb.append("【角色设定】\n").append(role.trim());
        }
        if (StringUtils.hasText(task)) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("【任务指令】\n").append(task.trim());
        }
        if (StringUtils.hasText(constraints)) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("【限制条件】\n").append(constraints.trim());
        }
        return sb.toString();
    }
}
