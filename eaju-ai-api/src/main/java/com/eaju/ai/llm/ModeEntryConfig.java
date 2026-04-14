package com.eaju.ai.llm;

import org.springframework.util.StringUtils;

/**
 * 单个逻辑 mode 在 {@code modes_json} 中的配置：上游 model id 与能力开关。
 */
public final class ModeEntryConfig {

    private final String upstreamModelId;
    private final boolean textGeneration;
    private final boolean deepThinking;
    private final boolean vision;
    /** 模型支持的最大上下文 token 数；null 表示未配置 */
    private final Integer contextWindow;

    public ModeEntryConfig(
            String upstreamModelId,
            boolean textGeneration,
            boolean deepThinking,
            boolean vision,
            Integer contextWindow) {
        this.upstreamModelId = upstreamModelId != null ? upstreamModelId : "";
        this.textGeneration = textGeneration;
        this.deepThinking = deepThinking;
        this.vision = vision;
        this.contextWindow = contextWindow;
    }

    public String getUpstreamModelId() {
        return upstreamModelId;
    }

    public boolean isTextGeneration() {
        return textGeneration;
    }

    public boolean isDeepThinking() {
        return deepThinking;
    }

    public boolean isVision() {
        return vision;
    }

    public Integer getContextWindow() {
        return contextWindow;
    }

    /**
     * @param legacyDeepThinkingDefault 值为 JSON 字符串的旧数据：是否默认开启「深度思考」能力标记
     */
    public static ModeEntryConfig fromLegacyStringValue(String logicalKey, String valueOrKey, boolean legacyDeepThinkingDefault) {
        String raw = StringUtils.hasText(valueOrKey) ? valueOrKey.trim() : logicalKey;
        return new ModeEntryConfig(raw, true, legacyDeepThinkingDefault, false, null);
    }
}
