package com.eaju.ai.llm;

import com.eaju.ai.llm.support.InferenceDefaults;

/**
 * 单个模型的运行时配置，由 {@link LlmModelEntity} 构建。
 */
public final class ModeEntryConfig {

    private final Long modelId;
    private final String upstreamModelId;
    private final boolean textGeneration;
    private final boolean deepThinking;
    private final boolean vision;
    private final boolean streamOutput;
    private final boolean toolCall;
    private final boolean forceThinkingEnabled;
    private final Integer contextWindow;
    private final InferenceDefaults inferenceDefaults;

    public ModeEntryConfig(
            Long modelId,
            String upstreamModelId,
            boolean textGeneration,
            boolean deepThinking,
            boolean vision,
            boolean streamOutput,
            boolean toolCall,
            boolean forceThinkingEnabled,
            Integer contextWindow,
            InferenceDefaults inferenceDefaults) {
        this.modelId = modelId;
        this.upstreamModelId = upstreamModelId != null ? upstreamModelId : "";
        this.textGeneration = textGeneration;
        this.deepThinking = deepThinking;
        this.vision = vision;
        this.streamOutput = streamOutput;
        this.toolCall = toolCall;
        this.forceThinkingEnabled = forceThinkingEnabled;
        this.contextWindow = contextWindow;
        this.inferenceDefaults = inferenceDefaults != null ? inferenceDefaults : new InferenceDefaults();
    }

    public Long getModelId() { return modelId; }
    public String getUpstreamModelId() { return upstreamModelId; }
    public boolean isTextGeneration() { return textGeneration; }
    public boolean isDeepThinking() { return deepThinking; }
    public boolean isVision() { return vision; }
    public boolean isStreamOutput() { return streamOutput; }
    public boolean isToolCall() { return toolCall; }
    public boolean isForceThinkingEnabled() { return forceThinkingEnabled; }
    public Integer getContextWindow() { return contextWindow; }
    public InferenceDefaults getInferenceDefaults() { return inferenceDefaults; }
}
