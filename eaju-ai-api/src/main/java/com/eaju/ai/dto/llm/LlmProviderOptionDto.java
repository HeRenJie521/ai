package com.eaju.ai.dto.llm;

import java.util.LinkedHashMap;
import java.util.Map;

/** 登录用户选择模型提供方（不含密钥） */
public class LlmProviderOptionDto {

    private Long id;
    private String code;
    private String displayName;
    private String defaultMode;
    /** 与库表 modes_json 一致：逻辑名(展示/请求键) → 上游 model id */
    private Map<String, String> modes = new LinkedHashMap<String, String>();
    /** 与 modes 键一致：每项能力（文本生成 / 深度思考 / 视觉） */
    private Map<String, ModeCapabilityDto> modeCapabilities = new LinkedHashMap<String, ModeCapabilityDto>();

    /** 是否支持在请求体中写 thinking（如 DeepSeek 官方兼容） */
    private boolean supportsThinking;
    /** 推理默认里是否开启思考模式（供聊天页开关初值） */
    private boolean defaultThinkingMode;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDefaultMode() {
        return defaultMode;
    }

    public void setDefaultMode(String defaultMode) {
        this.defaultMode = defaultMode;
    }

    public Map<String, String> getModes() {
        return modes;
    }

    public void setModes(Map<String, String> modes) {
        this.modes = modes != null ? new LinkedHashMap<String, String>(modes) : new LinkedHashMap<String, String>();
    }

    public Map<String, ModeCapabilityDto> getModeCapabilities() {
        return modeCapabilities;
    }

    public void setModeCapabilities(Map<String, ModeCapabilityDto> modeCapabilities) {
        this.modeCapabilities = modeCapabilities != null
                ? new LinkedHashMap<String, ModeCapabilityDto>(modeCapabilities)
                : new LinkedHashMap<String, ModeCapabilityDto>();
    }

    public boolean isSupportsThinking() {
        return supportsThinking;
    }

    public void setSupportsThinking(boolean supportsThinking) {
        this.supportsThinking = supportsThinking;
    }

    public boolean isDefaultThinkingMode() {
        return defaultThinkingMode;
    }

    public void setDefaultThinkingMode(boolean defaultThinkingMode) {
        this.defaultThinkingMode = defaultThinkingMode;
    }
}
