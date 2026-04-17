package com.eaju.ai.session;

import com.eaju.ai.dto.ChatMessageDto;
import com.eaju.ai.dto.ChatRequestDto;
import com.eaju.ai.dto.ChatResponseDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 使用 Redis 缓存多轮会话：按 {@code sessionId} 存完整 messages 列表（role+content）。
 */
@Service
public class ChatSessionService {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionService.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;
    private final long sessionTtlHours;

    public ChatSessionService(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.chat.redis-key-prefix:chat:session:}") String keyPrefix,
            @Value("${app.chat.redis-session-ttl-hours:168}") long sessionTtlHours) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix;
        this.sessionTtlHours = sessionTtlHours;
    }

    /**
     * 若有 {@code sessionId}，将 Redis 中的历史消息拼在本请求 {@code messages} 之前，得到发给模型的完整上下文。
     */
    public ChatRequestDto mergeHistory(ChatRequestDto original) {
        if (!StringUtils.hasText(original.getSessionId())) {
            return original;
        }
        List<ChatMessageDto> history = loadHistory(original.getSessionId().trim());
        if (history.isEmpty()) {
            return original;
        }
        List<ChatMessageDto> merged = new ArrayList<ChatMessageDto>(history);
        merged.addAll(original.getMessages());
        ChatRequestDto copy = new ChatRequestDto();
        BeanUtils.copyProperties(original, copy, "messages");
        copy.setMessages(merged);
        return copy;
    }

    /**
     * 阻塞对话成功后：把「已发给模型的上下文 + 助手回复」写回 Redis。
     */
    public void appendAssistantToSession(ChatRequestDto original,
                                         ChatRequestDto sentToModel,
                                         ChatResponseDto response) {
        if (!StringUtils.hasText(original.getSessionId())) {
            return;
        }
        try {
            List<ChatMessageDto> next = new ArrayList<ChatMessageDto>(sentToModel.getMessages());
            ChatMessageDto assistant = new ChatMessageDto();
            assistant.setRole("assistant");
            assistant.setContent(response.getContent() != null ? response.getContent() : "");
            if (StringUtils.hasText(response.getReasoningContent())) {
                assistant.setReasoningContent(response.getReasoningContent());
            }
            next.add(assistant);
            saveHistory(original.getSessionId().trim(), next);
        } catch (Exception ex) {
            log.warn("更新 Redis 会话失败 sessionId={}: {}", original.getSessionId(), ex.getMessage());
        }
    }

    /**
     * 工具调用结束后：将完整消息链（含 tool_calls / tool 结果）写入 Redis，使下轮对话能复用工具结果。
     * 系统消息（role=system）不写入，避免下轮 withSystemPrompt 重复注入时产生重复。
     */
    public void appendToolCallToSession(ChatRequestDto original,
                                        List<ChatMessageDto> workMessages,
                                        ChatResponseDto response) {
        if (!StringUtils.hasText(original.getSessionId())) return;
        try {
            List<ChatMessageDto> next = new ArrayList<>();
            for (ChatMessageDto msg : workMessages) {
                if (!"system".equals(msg.getRole())) {
                    next.add(msg);
                }
            }
            ChatMessageDto assistant = new ChatMessageDto();
            assistant.setRole("assistant");
            assistant.setContent(response.getContent() != null ? response.getContent() : "");
            next.add(assistant);
            saveHistory(original.getSessionId().trim(), next);
        } catch (Exception ex) {
            log.warn("更新工具调用 Redis 会话失败 sessionId={}: {}", original.getSessionId(), ex.getMessage());
        }
    }

    public List<ChatMessageDto> loadHistory(String sessionId) {
        try {
            String key = keyPrefix + sessionId;
            String json = stringRedisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(json)) {
                return Collections.emptyList();
            }
            return objectMapper.readValue(json, new TypeReference<List<ChatMessageDto>>() {
            });
        } catch (Exception ex) {
            log.warn("读取 Redis 会话失败 sessionId={}: {}", sessionId, ex.getMessage());
            return Collections.emptyList();
        }
    }

    public void saveHistory(String sessionId, List<ChatMessageDto> messages) throws Exception {
        String key = keyPrefix + sessionId;
        String json = objectMapper.writeValueAsString(messages);
        stringRedisTemplate.opsForValue().set(key, json, sessionTtlHours, TimeUnit.HOURS);
    }

    /** 删除会话在 Redis 中的消息缓存（左侧删除会话时调用） */
    public void deleteSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        try {
            stringRedisTemplate.delete(keyPrefix + sessionId.trim());
        } catch (Exception ex) {
            log.warn("删除 Redis 会话失败 sessionId={}: {}", sessionId, ex.getMessage());
        }
    }
}
