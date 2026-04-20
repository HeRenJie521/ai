package com.eaju.ai.persistence.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.time.Instant;

/**
 * 单次对话轮次：本回合用户侧消息（JSON）与助手回复、token 与上游元数据。
 */
@Entity
@Table(name = "chat_turn", indexes = {
        @Index(name = "idx_chat_turn_session_id", columnList = "session_id"),
        @Index(name = "idx_chat_turn_created_at", columnList = "created_at")
})
public class ChatTurnEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(name = "user_id", length = 128)
    private String userId;

    @Column(name = "provider", length = 64, nullable = false)
    private String provider;

    @Column(name = "model", length = 256)
    private String model;

    /** 本请求中客户端传入的 messages（JSON），不含 Redis 历史 */
    @Column(name = "client_messages_json", columnDefinition = "TEXT")
    private String clientMessagesJson;

    /** 实际发给大模型的完整 messages（JSON，含历史），便于审计与排错 */
    @Column(name = "request_messages_json", columnDefinition = "TEXT")
    private String requestMessagesJson;

    @Column(name = "assistant_content", columnDefinition = "TEXT")
    private String assistantContent;

    @Column(name = "reasoning_content", columnDefinition = "TEXT")
    private String reasoningContent;

    @Column(name = "upstream_message_id", length = 128)
    private String upstreamMessageId;

    @Column(name = "finish_reason", length = 64)
    private String finishReason;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "stream_mode", nullable = false)
    private boolean streamMode;

    /** 使用 X-API-Key 调用时记录 */
    @Column(name = "api_key_id")
    private Long apiKeyId;

    /** WEB_EMBED 集成 ID（api_key.id），用于按集成统计用量；非嵌入调用为 null */
    @Column(name = "integration_id")
    private Long integrationId;

    /** AI 应用嵌入 ID（ai_app.id），用于按应用统计用量；非应用嵌入调用为 null */
    @Column(name = "app_id")
    private Long appId;

    /** 本轮使用的模型 ID，关联 llm_model.id */
    @Column(name = "llm_model_id")
    private Long llmModelId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getClientMessagesJson() {
        return clientMessagesJson;
    }

    public void setClientMessagesJson(String clientMessagesJson) {
        this.clientMessagesJson = clientMessagesJson;
    }

    public String getRequestMessagesJson() {
        return requestMessagesJson;
    }

    public void setRequestMessagesJson(String requestMessagesJson) {
        this.requestMessagesJson = requestMessagesJson;
    }

    public String getAssistantContent() {
        return assistantContent;
    }

    public void setAssistantContent(String assistantContent) {
        this.assistantContent = assistantContent;
    }

    public String getReasoningContent() {
        return reasoningContent;
    }

    public void setReasoningContent(String reasoningContent) {
        this.reasoningContent = reasoningContent;
    }

    public String getUpstreamMessageId() {
        return upstreamMessageId;
    }

    public void setUpstreamMessageId(String upstreamMessageId) {
        this.upstreamMessageId = upstreamMessageId;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public boolean isStreamMode() {
        return streamMode;
    }

    public void setStreamMode(boolean streamMode) {
        this.streamMode = streamMode;
    }

    public Long getApiKeyId() {
        return apiKeyId;
    }

    public void setApiKeyId(Long apiKeyId) {
        this.apiKeyId = apiKeyId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getIntegrationId() {
        return integrationId;
    }

    public void setIntegrationId(Long integrationId) {
        this.integrationId = integrationId;
    }

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public Long getLlmModelId() {
        return llmModelId;
    }

    public void setLlmModelId(Long llmModelId) {
        this.llmModelId = llmModelId;
    }
}
