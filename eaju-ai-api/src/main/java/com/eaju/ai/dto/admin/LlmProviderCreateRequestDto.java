package com.eaju.ai.dto.admin;

import javax.validation.constraints.NotBlank;

/**
 * 简化创建：仅名称与连接信息；{@code code} 由服务端根据显示名称生成，默认 mode / modes / inference 使用占位并在「高级配置」中维护。
 */
public class LlmProviderCreateRequestDto {

    @NotBlank
    private String displayName;
    /** 可为空，后续在界面补全 */
    private String apiKey;
    @NotBlank
    private String baseUrl;
    /** 可选，默认 true */
    private Boolean enabled;
    /** 可选，默认由服务端排在末尾 */
    private Integer sortOrder;

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
