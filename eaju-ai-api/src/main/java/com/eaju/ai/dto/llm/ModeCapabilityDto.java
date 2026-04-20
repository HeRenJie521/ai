package com.eaju.ai.dto.llm;

public class ModeCapabilityDto {

    private boolean textGeneration;
    private boolean deepThinking;
    private boolean vision;
    private boolean streamOutput = true;
    private boolean toolCall = true;
    private Integer contextWindow;

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

    public Integer getContextWindow() { return contextWindow; }
    public void setContextWindow(Integer contextWindow) { this.contextWindow = contextWindow; }
}
