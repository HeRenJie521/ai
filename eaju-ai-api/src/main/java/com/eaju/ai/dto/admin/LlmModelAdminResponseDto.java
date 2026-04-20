package com.eaju.ai.dto.admin;

public class LlmModelAdminResponseDto {

    private Long id;
    private Long providerId;
    private String providerDisplayName;
    private String providerCode;
    private String name;
    private String upstreamModelId;
    // 能力
    private boolean textGeneration;
    private boolean deepThinking;
    private boolean vision;
    private boolean streamOutput;
    private boolean toolCall;
    private boolean forceThinkingEnabled;
    // 推理默认参数
    private Double temperature;
    private Integer maxTokens;
    private Double topP;
    private Integer topK;
    private Double frequencyPenalty;
    private Double presencePenalty;
    private String responseFormat;
    private Boolean thinkingMode;
    private Integer contextWindow;
    // 元信息
    private int sortOrder;
    private boolean enabled;
    private String createdAt;
    private String updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }

    public String getProviderDisplayName() { return providerDisplayName; }
    public void setProviderDisplayName(String providerDisplayName) { this.providerDisplayName = providerDisplayName; }

    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }

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

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public Double getTopP() { return topP; }
    public void setTopP(Double topP) { this.topP = topP; }

    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }

    public Double getFrequencyPenalty() { return frequencyPenalty; }
    public void setFrequencyPenalty(Double frequencyPenalty) { this.frequencyPenalty = frequencyPenalty; }

    public Double getPresencePenalty() { return presencePenalty; }
    public void setPresencePenalty(Double presencePenalty) { this.presencePenalty = presencePenalty; }

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

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
