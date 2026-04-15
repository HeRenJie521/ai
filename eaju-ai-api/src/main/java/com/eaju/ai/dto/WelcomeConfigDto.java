package com.eaju.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 开场引导配置响应 DTO
 */
public class WelcomeConfigDto {

    @JsonProperty("welcomeText")
    private String welcomeText;

    @JsonProperty("suggestions")
    private List<String> suggestions;

    /** AI 应用配置的默认模型 ID，供嵌入页面初始化模型选择 */
    @JsonProperty("modelId")
    private String modelId;

    public WelcomeConfigDto() {
    }

    public WelcomeConfigDto(String welcomeText, List<String> suggestions) {
        this.welcomeText = welcomeText;
        this.suggestions = suggestions;
    }

    public String getWelcomeText() {
        return welcomeText;
    }

    public void setWelcomeText(String welcomeText) {
        this.welcomeText = welcomeText;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }
}
