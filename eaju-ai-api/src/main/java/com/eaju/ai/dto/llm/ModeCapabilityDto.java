package com.eaju.ai.dto.llm;

/** 单个逻辑 mode 的能力标记（供聊天页展示/隐藏控件） */
public class ModeCapabilityDto {

    private boolean textGeneration;
    private boolean deepThinking;
    private boolean vision;
    /** 模型支持的最大上下文 token 数；null 表示未配置 */
    private Integer contextWindow;

    public boolean isTextGeneration() {
        return textGeneration;
    }

    public void setTextGeneration(boolean textGeneration) {
        this.textGeneration = textGeneration;
    }

    public boolean isDeepThinking() {
        return deepThinking;
    }

    public void setDeepThinking(boolean deepThinking) {
        this.deepThinking = deepThinking;
    }

    public boolean isVision() {
        return vision;
    }

    public void setVision(boolean vision) {
        this.vision = vision;
    }

    public Integer getContextWindow() {
        return contextWindow;
    }

    public void setContextWindow(Integer contextWindow) {
        this.contextWindow = contextWindow;
    }
}
