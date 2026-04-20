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
import com.eaju.ai.persistence.entity.UserContextFieldEntity;
import com.eaju.ai.persistence.repository.AiAppRepository;
import com.eaju.ai.persistence.repository.UserContextFieldRepository;
import com.eaju.ai.service.ToolCallOrchestrator.OrchestratorResult;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Service
public class ChatService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatService.class);
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
    private final UserContextFieldRepository userContextFieldRepository;
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
                       UserContextFieldRepository userContextFieldRepository,
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
        this.userContextFieldRepository = userContextFieldRepository;
        this.toolCallOrchestrator = toolCallOrchestrator;
    }

    public ChatResponseDto chat(ChatRequestDto request) {
        ChatRequestDto effective = chatSessionService.mergeHistory(request);
        ChatRequestDto forLlm = withSystemPrompt(request, effective);
        LlmProviderConfigSnapshot cfg = llmProviderConfigService.requireSnapshot(forLlm.getProvider());
        request.setInternalLlmModelId(cfg.getModeModelId(forLlm.getMode()));

        // 若当前应用绑定了工具，走工具调用编排循环
        List<AiToolEntity> tools = resolveTools(request.getInternalAppId());
        ChatResponseDto response;
        if (!tools.isEmpty()) {
            if (!cfg.modeSupportsToolCall(forLlm.getMode())) {
                response = new ChatResponseDto();
                response.setContent("当前模型不支持工具调用，请切换到支持工具调用的模型。");
                response.setFinishReason("error");
                return response;
            }
            Map<String, Object> userCtx = userContextCacheService.get(request.getInternalJti());
            OrchestratorResult result = toolCallOrchestrator.chat(forLlm, cfg, tools, userCtx, null);
            response = result.getResponse();
            chatSessionService.appendToolCallToSession(request, result.getWorkMessages(), response);
        } else {
            response = openAiLlmExecutor.chat(forLlm, cfg);
            chatSessionService.appendAssistantToSession(request, effective, response);
        }
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
                request.setInternalLlmModelId(cfg.getModeModelId(forLlm.getMode()));

                // 若应用绑定了工具，走非流式工具调用编排，再将最终回复模拟为 SSE 输出
                List<AiToolEntity> tools = resolveTools(request.getInternalAppId());
                if (!tools.isEmpty() && !cfg.modeSupportsToolCall(forLlm.getMode())) {
                    ChatResponseDto errResp = new ChatResponseDto();
                    errResp.setContent("当前模型不支持工具调用，请切换到支持工具调用的模型。");
                    errResp.setFinishReason("error");
                    emitResponseAsStream(emitter, errResp);
                    return;
                }
                if (!tools.isEmpty()) {
                    Map<String, Object> userCtx = userContextCacheService.get(request.getInternalJti());
                    Consumer<String> onProgress = buildProgressEmitter(emitter);
                    OrchestratorResult result = toolCallOrchestrator.chat(forLlm, cfg, tools, userCtx, onProgress);
                    ChatResponseDto response = result.getResponse();
                    chatSessionService.appendToolCallToSession(request, result.getWorkMessages(), response);
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
                log.warn("[对话异常] {}", ex.getMessage());
                String errorText = "调用失败：" + (ex.getMessage() != null ? ex.getMessage() : "未知错误");
                // 将错误信息作为 assistant 回复保存到会话历史和聊天记录
                ChatResponseDto errorResponse = new ChatResponseDto();
                errorResponse.setContent(errorText);
                errorResponse.setFinishReason("error");
                try {
                    chatSessionService.appendAssistantToSession(request, effective, errorResponse);
                } catch (Exception ignored) {}
                try {
                    chatRecordService.saveBlockingTurn(request, effective, errorResponse, false);
                } catch (Exception ignored) {}
                emitErrorAsStream(emitter, errorText);
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
        String systemContent = buildSystemContent(app, original.getInternalJti());
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

    private String buildSystemContent(AiAppEntity app, String jti) {
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
        // 添加工具调用复用策略指导
        if (sb.length() > 0) sb.append("\n\n");
        sb.append("【工具调用策略】\n");
        sb.append("1. 如果用户的问题可以基于历史对话中的工具调用结果直接回答，请不要再调用工具，直接使用已有数据回复。\n");
        sb.append("2. 只有当用户明确要求重新查询、更新数据、或询问新的数据时，才调用工具接口。\n");
        sb.append("3. 例如：用户第一次问有哪些单子，你调用了工具查询；当用户追问第一个单子的金额是多少，你应该直接使用已查到的数据回答，而不是再次调用工具。\n");

        // 注入当前日期时间，确保 AI 在生成工具参数时使用正确的时间
        if (sb.length() > 0) sb.append("\n\n");
        String now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss（E）"));
        sb.append("【当前时间】\n").append(now);

        // 注入用户信息（非 Object 类型字段），仅在 system prompt 中可见，前端无法获取
        String userInfo = buildUserInfoSection(jti);
        if (StringUtils.hasText(userInfo)) {
            sb.append("\n\n").append(userInfo);
        }
        return sb.toString();
    }

    /**
     * 从 Redis 用户上下文中提取非 Object 类型字段，拼接为系统提示中的【当前用户信息】段落。
     */
    private String buildUserInfoSection(String jti) {
        if (!StringUtils.hasText(jti)) return "";
        Map<String, Object> ctx = userContextCacheService.get(jti);
        if (ctx == null || ctx.isEmpty()) return "";
        List<UserContextFieldEntity> fields = userContextFieldRepository.findByEnabledIsTrueOrderByIdAsc();
        if (fields.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("【当前用户信息】\n");
        boolean hasAny = false;
        for (UserContextFieldEntity field : fields) {
            if ("Object".equalsIgnoreCase(field.getFieldType())) continue;
            Object value = ctx.get(field.getFieldKey());
            if (value == null) continue;
            String strValue = String.valueOf(value).trim();
            if (!StringUtils.hasText(strValue)) continue;
            sb.append(field.getLabel()).append("：").append(strValue).append("\n");
            hasAny = true;
        }
        return hasAny ? sb.toString().trim() : "";
    }

    /**
     * 将异常信息以普通文本 chunk 形式发送给客户端，错误直接显示在会话中。
     */
    private void emitErrorAsStream(SseEmitter emitter, String text) {
        try {
            ObjectNode chunk = objectMapper.createObjectNode();
            chunk.put("id", "error");
            chunk.put("object", "chat.completion.chunk");
            ArrayNode choices = chunk.putArray("choices");
            ObjectNode choice = choices.addObject();
            choice.put("index", 0);
            choice.putObject("delta").put("content", text);
            choice.putNull("finish_reason");
            emitter.send(SseEmitter.event().name("chunk").data(objectMapper.writeValueAsString(chunk)));
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

    /**
     * 构建进度 SSE 推送回调：将进度文本作为流式 delta chunk 发送给客户端。
     */
    private Consumer<String> buildProgressEmitter(SseEmitter emitter) {
        return text -> {
            try {
                ObjectNode chunk = objectMapper.createObjectNode();
                chunk.put("id", "progress");
                chunk.put("object", "chat.completion.chunk");
                ArrayNode choices = chunk.putArray("choices");
                ObjectNode choice = choices.addObject();
                choice.put("index", 0);
                choice.putObject("delta").put("content", text);
                choice.putNull("finish_reason");
                emitter.send(SseEmitter.event().name("chunk").data(objectMapper.writeValueAsString(chunk)));
            } catch (Exception ignored) {
            }
        };
    }
}
