package com.eaju.ai.dto.admin;

public class LlmProviderAdminResponseDto {

    private Long id;
    private String code;
    private String displayName;
    /** 脱敏展示 */
    private String apiKeyMasked;
    /** 仅 GET 单条详情时返回，供编辑表单回填 */
    private String apiKey;
    private String baseUrl;
    private String defaultMode;
    private String modesJson;
    private String inferenceDefaultsJson;
    private boolean enabled;
    private int sortOrder;
    private String createdAt;
    private String updatedAt;

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

    public String getApiKeyMasked() {
        return apiKeyMasked;
    }

    public void setApiKeyMasked(String apiKeyMasked) {
        this.apiKeyMasked = apiKeyMasked;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
