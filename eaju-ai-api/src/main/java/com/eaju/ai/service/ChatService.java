package com.eaju.ai.service;

import com.eaju.ai.dto.ChatRequestDto;
import com.eaju.ai.dto.ChatResponseDto;
import com.eaju.ai.llm.LlmProviderConfigSnapshot;
import com.eaju.ai.llm.OpenAiLlmExecutor;
import com.eaju.ai.llm.support.OpenAiStreamAccumulator;
import com.eaju.ai.persistence.ChatRecordService;
import com.eaju.ai.session.ChatSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.Closeable;
import java.io.IOException;
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

    public ChatService(LlmProviderConfigService llmProviderConfigService,
                       OpenAiLlmExecutor openAiLlmExecutor,
                       @Qualifier("chatStreamExecutor") ExecutorService chatStreamExecutor,
                       ChatSessionService chatSessionService,
                       ChatRecordService chatRecordService,
                       ObjectMapper objectMapper) {
        this.llmProviderConfigService = llmProviderConfigService;
        this.openAiLlmExecutor = openAiLlmExecutor;
        this.chatStreamExecutor = chatStreamExecutor;
        this.chatSessionService = chatSessionService;
        this.chatRecordService = chatRecordService;
        this.objectMapper = objectMapper;
    }

    public ChatResponseDto chat(ChatRequestDto request) {
        ChatRequestDto effective = chatSessionService.mergeHistory(request);
        LlmProviderConfigSnapshot cfg = llmProviderConfigService.requireSnapshot(effective.getProvider());
        ChatResponseDto response = openAiLlmExecutor.chat(effective, cfg);
        if (!cfg.resolveThinkingContentWanted(effective)) {
            response.setReasoningContent(null);
        }
        chatSessionService.appendAssistantToSession(request, effective, response);
        chatRecordService.saveBlockingTurn(request, effective, response, false);
        return response;
    }

    public SseEmitter chatStream(ChatRequestDto request) {
        ChatRequestDto effective = chatSessionService.mergeHistory(request);
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
                LlmProviderConfigSnapshot cfg = llmProviderConfigService.requireSnapshot(effective.getProvider());
                boolean captureReasoning = cfg.resolveThinkingContentWanted(effective);
                OpenAiStreamAccumulator accum = new OpenAiStreamAccumulator(objectMapper, captureReasoning);
                String resolvedModel = cfg.resolveUpstreamModelId(effective.getModel(), effective.getMode());
                openAiLlmExecutor.chatStream(effective, cfg, emitter, accum::acceptChunkJson, upstreamHolder);
                if (accum.hasAssistantPayload()) {
                    ChatResponseDto response = accum.toResponse(cfg.getCode(), resolvedModel);
                    if (!captureReasoning) {
                        response.setReasoningContent(null);
                    }
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
}
