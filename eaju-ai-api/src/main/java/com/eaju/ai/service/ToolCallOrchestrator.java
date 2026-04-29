package com.eaju.ai.service;

import com.eaju.ai.dto.ChatMessageDto;
import com.eaju.ai.dto.ChatRequestDto;
import com.eaju.ai.dto.ChatResponseDto;
import com.eaju.ai.llm.LlmProviderConfigSnapshot;
import com.eaju.ai.llm.OpenAiLlmExecutor;
import com.eaju.ai.persistence.entity.AiToolEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * AI 工具调用编排器：实现 function calling 循环（最多 MAX_ROUNDS 轮）。
 * <p>
 * 流程：
 * 1. 携带工具定义调用 LLM；
 * 2. 若响应含 tool_calls，依次执行工具并追加 tool 消息；
 * 3. 携带工具结果再次调用 LLM；
 * 4. 直到 LLM 返回普通文本（finish_reason=stop）或达到最大轮次。
 */
@Component
public class ToolCallOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ToolCallOrchestrator.class);
    private static final int MAX_ROUNDS = 5;

    private final OpenAiLlmExecutor openAiLlmExecutor;
    private final ToolCallExecutor toolCallExecutor;
    private final ObjectMapper objectMapper;

    public ToolCallOrchestrator(OpenAiLlmExecutor openAiLlmExecutor,
                                ToolCallExecutor toolCallExecutor,
                                ObjectMapper objectMapper) {
        this.openAiLlmExecutor = openAiLlmExecutor;
        this.toolCallExecutor = toolCallExecutor;
        this.objectMapper = objectMapper;
    }

    /**
     * 流式工具调用编排：工具调用阶段使用非流式，最终回复阶段使用流式。
     *
     * @param request  合并了历史记录的请求
     * @param cfg      LLM 配置快照
     * @param tools    本次对话可用的工具列表
     * @param userCtx  当前用户上下文
     * @param onProgress 进度回调（推送工具调用进度）
     * @param streamEmitter SSE 发射器，用于流式发送最终回复
     * @param onEachChunkJson 每个流式 chunk 的回调
     * @param upstreamHolder 上游连接持有者
     * @return 流式编排结果，包含工作消息列表
     * @throws Exception 异常
     */
    public StreamOrchestratorResult chatStream(ChatRequestDto request, LlmProviderConfigSnapshot cfg,
                           List<AiToolEntity> tools, Map<String, Object> userCtx,
                           Consumer<String> onProgress,
                           SseEmitter streamEmitter,
                           Consumer<String> onEachChunkJson,
                           AtomicReference<Closeable> upstreamHolder) throws Exception {
        ArrayNode toolsArray = buildToolsArray(tools, null);
        List<ChatMessageDto> workMessages = new ArrayList<>(request.getMessages());

        ChatResponseDto lastResponse = null;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            ChatRequestDto roundRequest = copyWithMessages(request, workMessages);

            // 最后一轮（没有 tool_calls 或达到最大轮次）使用流式请求
            boolean isFinalRound = (round == MAX_ROUNDS - 1);
            if (isFinalRound) {
                log.info("[工具编排-流式] 第 {} 轮，使用流式请求", round + 1);
                openAiLlmExecutor.chatStream(roundRequest, cfg, streamEmitter, onEachChunkJson, upstreamHolder);
                return new StreamOrchestratorResult(workMessages);
            }

            // 非最后一轮使用非流式请求，获取工具调用指令
            lastResponse = openAiLlmExecutor.chatWithTools(roundRequest, cfg, toolsArray);

            JsonNode raw = lastResponse.getRaw();
            List<ToolCallItem> toolCalls = extractToolCalls(raw);
            if (toolCalls.isEmpty()) {
                // 响应里没有 tool_calls，是普通文本回复，结束循环
                break;
            }

            String finishReason = lastResponse.getFinishReason();
            log.info("[工具编排] 第 {} 轮，共 {} 个工具调用，finish_reason={}", round + 1, toolCalls.size(), finishReason);

            ChatMessageDto assistantMsg = buildAssistantToolCallMessage(raw);
            workMessages.add(assistantMsg);

            for (ToolCallItem tc : toolCalls) {
                AiToolEntity matchedTool = findTool(tools, tc.functionName);

                if (onProgress != null) {
                    String label = matchedTool != null && StringUtils.hasText(matchedTool.getLabel())
                            ? matchedTool.getLabel() : "数据";
                    onProgress.accept("正在" + label + "，请稍后...\n\n");
                }

                String result;
                if (matchedTool == null) {
                    log.warn("[工具编排] 未找到工具定义: {}", tc.functionName);
                    result = "{\"error\": \"未找到工具: " + tc.functionName + "\"}";
                } else {
                    log.info("[工具编排] 调用工具={} id={} 入参={}", tc.functionName, tc.id, tc.arguments);
                    result = toolCallExecutor.execute(matchedTool, tc.arguments, userCtx,
                            request.getInternalExtendedParams());
                    log.info("[工具编排] 工具={} 返回结果={}", tc.functionName, result);
                }

                ChatMessageDto toolMsg = new ChatMessageDto();
                toolMsg.setRole("tool");
                toolMsg.setToolCallId(tc.id);
                toolMsg.setContent(result);
                workMessages.add(toolMsg);
            }
        }

        // 最终回复使用流式请求
        log.info("[工具编排-流式] 工具调用完成，发送流式最终回复");
        ChatRequestDto finalRequest = copyWithMessages(request, workMessages);
        openAiLlmExecutor.chatStream(finalRequest, cfg, streamEmitter, onEachChunkJson, upstreamHolder);
        return new StreamOrchestratorResult(workMessages);
    }

    /**
     * 流式工具调用编排结果
     */
    public static class StreamOrchestratorResult {
        private final List<ChatMessageDto> workMessages;

        public StreamOrchestratorResult(List<ChatMessageDto> workMessages) {
            this.workMessages = workMessages;
        }

        public List<ChatMessageDto> getWorkMessages() {
            return workMessages;
        }
    }

    /**
     * 执行带工具调用的对话。
     *
     * @param request  合并了历史记录的请求
     * @param cfg      LLM 配置快照
     * @param tools    本次对话可用的工具列表
     * @param userCtx    当前用户上下文（来自 Redis ctx:{jti}）
     * @param onProgress 进度回调，每次调用工具前后发送提示文本；流式场景传入，非流式传 null
     * @return 最终 LLM 文本响应
     */
    public OrchestratorResult chat(ChatRequestDto request, LlmProviderConfigSnapshot cfg,
                                   List<AiToolEntity> tools, Map<String, Object> userCtx,
                                   Consumer<String> onProgress) {
        // 构建 tools 数组（OpenAI function calling 格式）
        ArrayNode toolsArray = buildToolsArray(tools, null);

        // 工作消息列表（初始为请求中的消息，循环中不断追加）
        List<ChatMessageDto> workMessages = new ArrayList<>(request.getMessages());

        ChatResponseDto lastResponse = null;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            ChatRequestDto roundRequest = copyWithMessages(request, workMessages);
            lastResponse = openAiLlmExecutor.chatWithTools(roundRequest, cfg, toolsArray);

            // 兼容各模型：优先从响应消息体中判断是否有 tool_calls，
            // 不依赖 finish_reason（部分模型用 "stop" 而非 "tool_calls"）
            JsonNode raw = lastResponse.getRaw();
            List<ToolCallItem> toolCalls = extractToolCalls(raw);
            if (toolCalls.isEmpty()) {
                // 响应里没有 tool_calls，是普通文本回复，结束循环
                break;
            }

            String finishReason = lastResponse.getFinishReason();
            log.info("[工具编排] 第 {} 轮，共 {} 个工具调用，finish_reason={}", round + 1, toolCalls.size(), finishReason);

            // 追加 assistant 消息（含 tool_calls）
            ChatMessageDto assistantMsg = buildAssistantToolCallMessage(raw);
            workMessages.add(assistantMsg);

            // 执行每个工具并追加 tool 消息
            for (ToolCallItem tc : toolCalls) {
                AiToolEntity matchedTool = findTool(tools, tc.functionName);

                // 工具调用前：推送查询进度提示（使用 label 字段）
                if (onProgress != null) {
                    String label = matchedTool != null && StringUtils.hasText(matchedTool.getLabel())
                            ? matchedTool.getLabel() : "数据";
                    onProgress.accept("正在" + label + "，请稍后...\n\n");
                }

                String result;
                if (matchedTool == null) {
                    log.warn("[工具编排] 未找到工具定义: {}", tc.functionName);
                    result = "{\"error\": \"未找到工具: " + tc.functionName + "\"}";
                } else {
                    log.info("[工具编排] 调用工具={} id={} 入参={}", tc.functionName, tc.id, tc.arguments);
                    result = toolCallExecutor.execute(matchedTool, tc.arguments, userCtx,
                            request.getInternalExtendedParams());
                    log.info("[工具编排] 工具={} 返回结果={}", tc.functionName, result);
                }

                // 追加 tool 消息
                ChatMessageDto toolMsg = new ChatMessageDto();
                toolMsg.setRole("tool");
                toolMsg.setToolCallId(tc.id);
                toolMsg.setContent(result);
                workMessages.add(toolMsg);
            }
        }

        // 兜底：部分模型（如 Kimi）在收到工具结果后仍可能返回空内容，
        // 去掉 tools 参数再调一次，强制模型输出总结性文本
        boolean hasToolResult = workMessages.stream().anyMatch(m -> "tool".equals(m.getRole()));
        if (hasToolResult && lastResponse != null && !StringUtils.hasText(lastResponse.getContent())) {
            log.warn("[工具编排] 最终回复为空，不带工具重试一次以强制生成文本");
            try {
                ChatRequestDto retryRequest = copyWithMessages(request, workMessages);
                lastResponse = openAiLlmExecutor.chatWithTools(retryRequest, cfg, null);
            } catch (Exception e) {
                log.warn("[工具编排] 重试失败：{}", e.getMessage());
            }
        }

        return new OrchestratorResult(lastResponse != null ? lastResponse.getContent() : "", workMessages, lastResponse);
    }

    /**
     * 工具调用结果封装
     */
    public static class OrchestratorResult {
        private final String content;
        private final List<ChatMessageDto> workMessages;
        private final ChatResponseDto response;

        public OrchestratorResult(String content, List<ChatMessageDto> workMessages, ChatResponseDto response) {
            this.content = content;
            this.workMessages = workMessages;
            this.response = response;
        }

        public String getContent() { return content; }
        public List<ChatMessageDto> getWorkMessages() { return workMessages; }
        public ChatResponseDto getResponse() { return response; }
    }

    /**
     * 构建 tools 数组（OpenAI function calling 格式）。
     * @param tools 工具列表
     * @param callStrategies 工具调用策略 Map，key=toolId, value=callStrategy
     */
    private ArrayNode buildToolsArray(List<AiToolEntity> tools, Map<Long, String> callStrategies) {
        ArrayNode array = objectMapper.createArrayNode();
        for (AiToolEntity tool : tools) {
            ObjectNode toolNode = objectMapper.createObjectNode();
            toolNode.put("type", "function");
            ObjectNode fn = objectMapper.createObjectNode();
            fn.put("name", tool.getName());
            
            // 如果有调用策略，将其追加到描述中
            String description = tool.getDescription();
            String callStrategy = callStrategies != null ? callStrategies.get(tool.getId()) : null;
            if (StringUtils.hasText(callStrategy)) {
                description = description + "\n\n【调用策略】\n" + callStrategy;
            }
            fn.put("description", description);
            
            // 解析 paramsSchemaJson 并内嵌
            try {
                JsonNode schema = objectMapper.readTree(tool.getParamsSchemaJson());
                fn.set("parameters", schema);
            } catch (Exception e) {
                log.warn("解析工具参数 Schema 失败：tool={} err={}", tool.getName(), e.getMessage());
                fn.set("parameters", objectMapper.createObjectNode());
            }
            toolNode.set("function", fn);
            array.add(toolNode);
        }
        return array;
    }

    private List<ToolCallItem> extractToolCalls(JsonNode raw) {
        List<ToolCallItem> list = new ArrayList<>();
        if (raw == null) return list;
        JsonNode choices = raw.get("choices");
        if (choices == null || !choices.isArray() || choices.size() == 0) return list;
        JsonNode message = choices.get(0).get("message");
        if (message == null) return list;
        JsonNode toolCalls = message.get("tool_calls");
        if (toolCalls == null || !toolCalls.isArray()) return list;
        for (JsonNode tc : toolCalls) {
            String id = tc.path("id").asText(null);
            JsonNode fn = tc.get("function");
            if (fn == null) continue;
            String name = fn.path("name").asText(null);
            String args = fn.path("arguments").asText("{}");
            if (StringUtils.hasText(name)) {
                list.add(new ToolCallItem(id, name, args));
            }
        }
        return list;
    }

    private ChatMessageDto buildAssistantToolCallMessage(JsonNode raw) {
        ChatMessageDto msg = new ChatMessageDto();
        msg.setRole("assistant");
        msg.setContent(null);
        // 将 raw message 节点中的 tool_calls 存回（供下一轮请求发送给 LLM）
        if (raw != null) {
            JsonNode choices = raw.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    JsonNode toolCallsNode = message.get("tool_calls");
                    if (toolCallsNode != null) {
                        msg.setToolCallsJson(toolCallsNode.toString());
                    }
                    // 若 content 有值也带上
                    JsonNode contentNode = message.get("content");
                    if (contentNode != null && !contentNode.isNull()) {
                        msg.setContent(contentNode.asText());
                    }
                }
            }
        }
        return msg;
    }

    private static AiToolEntity findTool(List<AiToolEntity> tools, String name) {
        for (AiToolEntity t : tools) {
            if (t.getName().equals(name)) return t;
        }
        return null;
    }

    private static ChatRequestDto copyWithMessages(ChatRequestDto src, List<ChatMessageDto> messages) {
        ChatRequestDto copy = new ChatRequestDto();
        BeanUtils.copyProperties(src, copy);
        copy.setMessages(new ArrayList<>(messages));
        return copy;
    }

    private static class ToolCallItem {
        final String id;
        final String functionName;
        final String arguments;

        ToolCallItem(String id, String functionName, String arguments) {
            this.id = id;
            this.functionName = functionName;
            this.arguments = arguments;
        }
    }
}
