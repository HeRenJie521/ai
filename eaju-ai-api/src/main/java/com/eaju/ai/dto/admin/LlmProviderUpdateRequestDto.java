package com.eaju.ai.dto.admin;

/**
 * 部分更新：字段为 {@code null} 表示不修改（JSON 省略该字段）。
 */
public class LlmProviderUpdateRequestDto {

    private String code;
    private String displayName;
    private String apiKey;
    private String baseUrl;
    private String defaultMode;
    private String modesJson;
    private String inferenceDefaultsJson;
    private Boolean enabled;
    private Integer sortOrder;

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

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getDefaultMode() {
        return defaultMode;
    }

    public void setDefaultMode(String defaultMode) {
        this.defaultMode = defaultMode;
    }

    public String getModesJson() {
        return modesJson;
    }

    public void setModesJson(String modesJson) {
        this.modesJson = modesJson;
    }

    public String getInferenceDefaultsJson() {
        return inferenceDefaultsJson;
    }

    public void setInferenceDefaultsJson(String inferenceDefaultsJson) {
        this.inferenceDefaultsJson = inferenceDefaultsJson;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
