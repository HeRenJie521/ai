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
}
