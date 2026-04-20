package com.eaju.ai.dto.admin;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 新增或更新模型的请求体（新增时 providerId 必填，更新时可选）。
 */
public class LlmModelSaveRequestDto {

    @NotNull(message = "providerId 不能为空")
    private Long providerId;

    @NotBlank(message = "模型名称不能为空")
    private String name;

    /** 上游模型 ID，为空时默认与 name 一致 */
    private String upstreamModelId;

    // 能力
    private Boolean textGeneration;
    private Boolean deepThinking;
    private Boolean vision;
    private Boolean streamOutput;
    private Boolean toolCall;
    private Boolean forceThinkingEnabled;

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

    private Integer sortOrder;
    private Boolean enabled;

    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUpstreamModelId() { return upstreamModelId; }
    public void setUpstreamModelId(String upstreamModelId) { this.upstreamModelId = upstreamModelId; }

    public Boolean getTextGeneration() { return textGeneration; }
    public void setTextGeneration(Boolean textGeneration) { this.textGeneration = textGeneration; }

    public Boolean getDeepThinking() { return deepThinking; }
    public void setDeepThinking(Boolean deepThinking) { this.deepThinking = deepThinking; }

    public Boolean getVision() { return vision; }
    public void setVision(Boolean vision) { this.vision = vision; }

    public Boolean getStreamOutput() { return streamOutput; }
    public void setStreamOutput(Boolean streamOutput) { this.streamOutput = streamOutput; }

    public Boolean getToolCall() { return toolCall; }
    public void setToolCall(Boolean toolCall) { this.toolCall = toolCall; }

    public Boolean getForceThinkingEnabled() { return forceThinkingEnabled; }
    public void setForceThinkingEnabled(Boolean forceThinkingEnabled) { this.forceThinkingEnabled = forceThinkingEnabled; }

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

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
