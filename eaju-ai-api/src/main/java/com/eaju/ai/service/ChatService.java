package com.eaju.ai.service;

import com.eaju.ai.dto.ChatMessageDto;
import com.eaju.ai.dto.ChatRequestDto;
import com.eaju.ai.dto.ChatResponseDto;
import com.eaju.ai.llm.LlmProviderConfigSnapshot;
import com.eaju.ai.llm.OpenAiLlmExecutor;
import com.eaju.ai.llm.support.OpenAiStreamAccumulator;
import com.eaju.ai.persistence.ChatRecordService;
import com.eaju.ai.persistence.entity.AiAppEntity;
import com.eaju.ai.persistence.entity.AiToolEntity;
import com.eaju.ai.persistence.repository.AiAppRepository;
import com.eaju.ai.session.ChatSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private final AiAppRepository aiAppRepository;
    private final AiToolService aiToolService;
    private final UserContextCacheService userContextCacheService;
    private final ToolCallOrchestrator toolCallOrchestrator;

    public ChatService(LlmProviderConfigService llmProviderConfigService,
                       OpenAiLlmExecutor openAiLlmExecutor,
                       @Qualifier("chatStreamExecutor") ExecutorService chatStreamExecutor,
                       ChatSessionService chatSessionService,
                       ChatRecordService chatRecordService,
                       ObjectMapper objectMapper,
                       AiAppRepository aiAppRepository,
                       AiToolService aiToolService,
                       UserContextCacheService userContextCacheService,
                       ToolCallOrchestrator toolCallOrchestrator) {
        this.llmProviderConfigService = llmProviderConfigService;
        this.openAiLlmExecutor = openAiLlmExecutor;
        this.chatStreamExecutor = chatStreamExecutor;
        this.chatSessionService = chatSessionService;
        this.chatRecordService = chatRecordService;
        this.objectMapper = objectMapper;
        this.aiAppRepository = aiAppRepository;
        this.aiToolService = aiToolService;
        this.userContextCacheService = userContextCacheService;
        this.toolCallOrchestrator = toolCallOrchestrator;
    }

    public ChatResponseDto chat(ChatRequestDto request) {
        ChatRequestDto effective = chatSessionService.mergeHistory(request);
        ChatRequestDto forLlm = withSystemPrompt(request, effective);
        LlmProviderConfigSnapshot cfg = llmProviderConfigService.requireSnapshot(forLlm.getProvider());

        // 若当前应用绑定了工具，走工具调用编排循环
        List<AiToolEntity> tools = resolveTools(request.getInternalAppId());
        ChatResponseDto response;
        if (!tools.isEmpty()) {
            Map<String, Object> userCtx = userContextCacheService.get(request.getInternalJti());
            response = toolCallOrchestrator.chat(forLlm, cfg, tools, userCtx);
        } else {
            response = openAiLlmExecutor.chat(forLlm, cfg);
        }

        chatSessionService.appendAssistantToSession(request, effective, response);
        chatRecordService.saveBlockingTurn(request, effective, response, false);
        if (!cfg.resolveThinkingContentWanted(forLlm)) {
            response.setReasoningContent(null);
        }
        return response;
    }

    private List<AiToolEntity> resolveTools(Long appId) {
        if (appId == null) return Collections.emptyList();
        return aiToolService.findEnabledToolsByAppId(appId);
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

                // 若应用绑定了工具，走非流式工具调用编排，再将最终回复模拟为 SSE 输出
                List<AiToolEntity> tools = resolveTools(request.getInternalAppId());
                if (!tools.isEmpty()) {
                    Map<String, Object> userCtx = userContextCacheService.get(request.getInternalJti());
                    ChatResponseDto response = toolCallOrchestrator.chat(forLlm, cfg, tools, userCtx);
                    chatSessionService.appendAssistantToSession(request, effective, response);
                    chatRecordService.saveBlockingTurn(request, effective, response, false);
                    if (!cfg.resolveThinkingContentWanted(forLlm)) {
                        response.setReasoningContent(null);
                    }
                    emitResponseAsStream(emitter, response);
                    return;
                }

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
     * 将工具调用编排后的最终回复以 SSE chunk 格式发送给客户端，模拟流式输出。
     */
    private void emitResponseAsStream(SseEmitter emitter, ChatResponseDto response) {
        try {
            String content = response.getContent() != null ? response.getContent() : "";
            ObjectNode chunk = objectMapper.createObjectNode();
            chunk.put("id", response.getId() != null ? response.getId() : "tool-resp");
            chunk.put("object", "chat.completion.chunk");
            ArrayNode choices = chunk.putArray("choices");
            ObjectNode choice = choices.addObject();
            choice.put("index", 0);
            ObjectNode delta = choice.putObject("delta");
            delta.put("content", content);
            choice.putNull("finish_reason");
            emitter.send(SseEmitter.event().name("chunk").data(objectMapper.writeValueAsString(chunk)));
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
        } catch (Exception e) {
            try { emitter.completeWithError(e); } catch (Exception ignored) { }
        }
    }

    /**
     * 若当前请求来自集成且关联了 AI 应用，则将 system message 注入到发给 LLM 的消息列表首位。
     * 通过 integration.app_id → ai_app 加载系统提示词。
     * 返回的新 DTO 仅用于调用 LLM，不会写入 Redis 会话历史，确保每轮请求都带最新配置。
     */
    private ChatRequestDto withSystemPrompt(ChatRequestDto original, ChatRequestDto effective) {
        // 应用管理嵌入：JWT 中直接携带 appId，据此加载 AI 应用配置
        Long directAppId = original.getInternalAppId();
        if (directAppId == null) {
            return effective;
        }
        AiAppEntity app = aiAppRepository.findByIdAndDeletedIsFalse(directAppId).orElse(null);
        if (app == null) {
            return effective;
        }
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
