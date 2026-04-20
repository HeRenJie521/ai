package com.eaju.ai.persistence.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "llm_model",
        indexes = {
                @Index(name = "idx_llm_model_provider_id",  columnList = "provider_id"),
                @Index(name = "idx_llm_model_enabled_sort", columnList = "enabled,sort_order")
        }
)
public class LlmModelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    /** 逻辑名：/chat 请求体 mode 参数值，也是前端展示名称 */
    @Column(nullable = false, length = 256)
    private String name;

    /** 发往上游 API 的实际 model 字段值 */
    @Column(name = "upstream_model_id", nullable = false, length = 256)
    private String upstreamModelId;

    // ----- 能力标志 -----
    @Column(name = "text_generation", nullable = false)
    private boolean textGeneration = true;

    @Column(name = "deep_thinking", nullable = false)
    private boolean deepThinking = false;

    @Column(nullable = false)
    private boolean vision = false;

    @Column(name = "stream_output", nullable = false)
    private boolean streamOutput = true;

    @Column(name = "tool_call", nullable = false)
    private boolean toolCall = true;

    /** 是否强制开启 thinking（MiniMax 系列需要） */
    @Column(name = "force_thinking_enabled", nullable = false)
    private boolean forceThinkingEnabled = false;

    // ----- 每模型推理默认参数 -----
    @Column(precision = 5, scale = 2)
    private BigDecimal temperature;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    @Column(name = "top_p", precision = 5, scale = 2)
    private BigDecimal topP;

    @Column(name = "top_k")
    private Integer topK;

    @Column(name = "frequency_penalty", precision = 5, scale = 2)
    private BigDecimal frequencyPenalty;

    @Column(name = "presence_penalty", precision = 5, scale = 2)
    private BigDecimal presencePenalty;

    @Column(name = "response_format", length = 64)
    private String responseFormat;

    @Column(name = "thinking_mode")
    private Boolean thinkingMode;

    @Column(name = "context_window")
    private Integer contextWindow;

    // ----- 元信息 -----
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUpstreamModelId() { return upstreamModelId; }
    public void setUpstreamModelId(String upstreamModelId) { this.upstreamModelId = upstreamModelId; }

    public boolean isTextGeneration() { return textGeneration; }
    public void setTextGeneration(boolean textGeneration) { this.textGeneration = textGeneration; }

    public boolean isDeepThinking() { return deepThinking; }
    public void setDeepThinking(boolean deepThinking) { this.deepThinking = deepThinking; }

    public boolean isVision() { return vision; }
    public void setVision(boolean vision) { this.vision = vision; }

    public boolean isStreamOutput() { return streamOutput; }
    public void setStreamOutput(boolean streamOutput) { this.streamOutput = streamOutput; }

    public boolean isToolCall() { return toolCall; }
    public void setToolCall(boolean toolCall) { this.toolCall = toolCall; }

    public boolean isForceThinkingEnabled() { return forceThinkingEnabled; }
    public void setForceThinkingEnabled(boolean forceThinkingEnabled) { this.forceThinkingEnabled = forceThinkingEnabled; }

    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public BigDecimal getTopP() { return topP; }
    public void setTopP(BigDecimal topP) { this.topP = topP; }

    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }

    public BigDecimal getFrequencyPenalty() { return frequencyPenalty; }
    public void setFrequencyPenalty(BigDecimal frequencyPenalty) { this.frequencyPenalty = frequencyPenalty; }

    public BigDecimal getPresencePenalty() { return presencePenalty; }
    public void setPresencePenalty(BigDecimal presencePenalty) { this.presencePenalty = presencePenalty; }

    public String getResponseFormat() { return responseFormat; }
    public void setResponseFormat(String responseFormat) { this.responseFormat = responseFormat; }

    public Boolean getThinkingMode() { return thinkingMode; }
    public void setThinkingMode(Boolean thinkingMode) { this.thinkingMode = thinkingMode; }

    public Integer getContextWindow() { return contextWindow; }
    public void setContextWindow(Integer contextWindow) { this.contextWindow = contextWindow; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
