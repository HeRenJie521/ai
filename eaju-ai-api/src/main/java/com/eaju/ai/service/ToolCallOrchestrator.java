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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        ArrayNode toolsArray = buildToolsArray(tools);

        // 工作消息列表（初始为请求中的消息，循环中不断追加）
        List<ChatMessageDto> workMessages = new ArrayList<>(request.getMessages());

        ChatResponseDto lastResponse = null;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            ChatRequestDto roundRequest = copyWithMessages(request, workMessages);
            lastResponse = openAiLlmExecutor.chatWithTools(roundRequest, cfg, toolsArray);

            // 检查 finish_reason
            String finishReason = lastResponse.getFinishReason();
            if (!"tool_calls".equals(finishReason)) {
                // 普通回复，结束循环
                break;
            }

            // 解析 tool_calls
            JsonNode raw = lastResponse.getRaw();
            List<ToolCallItem> toolCalls = extractToolCalls(raw);
            if (toolCalls.isEmpty()) {
                break;
            }

            log.info("[工具编排] 第 {} 轮，共 {} 个工具调用", round + 1, toolCalls.size());

            // 追加 assistant 消息（含 tool_calls）
            ChatMessageDto assistantMsg = buildAssistantToolCallMessage(raw);
            workMessages.add(assistantMsg);

            // 执行每个工具并追加 tool 消息
            for (ToolCallItem tc : toolCalls) {
                AiToolEntity matchedTool = findTool(tools, tc.functionName);

                // 工具调用前：推送查询进度提示（使用 name 字段）
                if (onProgress != null) {
                    String name = matchedTool != null && StringUtils.hasText(matchedTool.getName())
                            ? matchedTool.getName() : "数据";
                    onProgress.accept("正在查询" + name + "，请稍后...\n\n");
                }

                log.info("[工具编排] 调用工具={} id={} 入参={}", tc.functionName, tc.id, tc.arguments);
                String result;
                if (matchedTool != null) {
                    result = toolCallExecutor.execute(matchedTool, tc.arguments, userCtx);
                } else {
                    log.warn("未找到工具定义: {}", tc.functionName);
                    result = "{\"error\": \"未找到工具: " + tc.functionName + "\"}";
                }
                log.info("[工具编排] 工具={} 返回结果={}", tc.functionName, result);
                ChatMessageDto toolMsg = new ChatMessageDto();
                toolMsg.setRole("tool");
                toolMsg.setContent(result);
                toolMsg.setToolCallId(tc.id);
                workMessages.add(toolMsg);
            }

            // 本轮所有工具执行完毕：推送 AI 分析进度提示
            if (onProgress != null) {
                onProgress.accept("正在分析数据...\n\n");
            }
        }

        return new OrchestratorResult(lastResponse, workMessages);
    }

    /** 工具调用编排结果：最终 LLM 响应 + 完整消息链（含工具调用中间消息） */
    public static class OrchestratorResult {
        private final ChatResponseDto response;
        private final List<ChatMessageDto> workMessages;

        public OrchestratorResult(ChatResponseDto response, List<ChatMessageDto> workMessages) {
            this.response = response;
            this.workMessages = workMessages;
        }

        public ChatResponseDto getResponse() { return response; }
        public List<ChatMessageDto> getWorkMessages() { return workMessages; }
    }

    private ArrayNode buildToolsArray(List<AiToolEntity> tools) {
        ArrayNode array = objectMapper.createArrayNode();
        for (AiToolEntity tool : tools) {
            ObjectNode toolNode = objectMapper.createObjectNode();
            toolNode.put("type", "function");
            ObjectNode fn = objectMapper.createObjectNode();
            fn.put("name", tool.getName());
            fn.put("description", tool.getDescription());
            // 解析 paramsSchemaJson 并内嵌
            try {
                JsonNode schema = objectMapper.readTree(tool.getParamsSchemaJson());
                fn.set("parameters", schema);
            } catch (Exception e) {
                log.warn("解析工具参数 Schema 失败: tool={} err={}", tool.getName(), e.getMessage());
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

    private static ChatRequestDto copyWithMessages(ChatRequestDto original, List<ChatMessageDto> messages) {
        ChatRequestDto copy = new ChatRequestDto();
        BeanUtils.copyProperties(original, copy, "messages");
        copy.setMessages(messages);
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
