package com.eaju.ai.dto.admin;

public class LlmProviderUpdateRequestDto {

    private String displayName;
    private String apiKey;
    private String baseUrl;
    private Boolean enabled;
    private Integer sortOrder;
    // 提供方行为标志
    private Double forceTemperature;
    private String thinkingParamStyle;
    private Boolean jsonModeSystemHint;
    private Boolean stripToolCallIndex;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Double getForceTemperature() { return forceTemperature; }
    public void setForceTemperature(Double forceTemperature) { this.forceTemperature = forceTemperature; }

    public String getThinkingParamStyle() { return thinkingParamStyle; }
    public void setThinkingParamStyle(String thinkingParamStyle) { this.thinkingParamStyle = thinkingParamStyle; }

    public Boolean getJsonModeSystemHint() { return jsonModeSystemHint; }
    public void setJsonModeSystemHint(Boolean jsonModeSystemHint) { this.jsonModeSystemHint = jsonModeSystemHint; }

    public Boolean getStripToolCallIndex() { return stripToolCallIndex; }
    public void setStripToolCallIndex(Boolean stripToolCallIndex) { this.stripToolCallIndex = stripToolCallIndex; }
}
