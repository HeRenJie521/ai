package com.eaju.ai.dto.admin;

public class LlmProviderAdminResponseDto {

    private Long id;
    private String code;
    private String displayName;
    private String apiKeyMasked;
    /** 仅 GET 单条详情时返回 */
    private String apiKey;
    private String baseUrl;
    private boolean enabled;
    private int sortOrder;
    // 提供方行为标志
    private Double forceTemperature;
    private String thinkingParamStyle;
    private boolean jsonModeSystemHint;
    private boolean stripToolCallIndex;
    private String createdAt;
    private String updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getApiKeyMasked() { return apiKeyMasked; }
    public void setApiKeyMasked(String apiKeyMasked) { this.apiKeyMasked = apiKeyMasked; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public Double getForceTemperature() { return forceTemperature; }
    public void setForceTemperature(Double forceTemperature) { this.forceTemperature = forceTemperature; }

    public String getThinkingParamStyle() { return thinkingParamStyle; }
    public void setThinkingParamStyle(String thinkingParamStyle) { this.thinkingParamStyle = thinkingParamStyle; }

    public boolean isJsonModeSystemHint() { return jsonModeSystemHint; }
    public void setJsonModeSystemHint(boolean jsonModeSystemHint) { this.jsonModeSystemHint = jsonModeSystemHint; }

    public boolean isStripToolCallIndex() { return stripToolCallIndex; }
    public void setStripToolCallIndex(boolean stripToolCallIndex) { this.stripToolCallIndex = stripToolCallIndex; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
