package com.eaju.ai.dto.admin;

public class ApiKeyPatchRequestDto {

    private String name;
    private Boolean enabled;

    /** WEB_EMBED 允许嵌入的来源域名，逗号分隔 */
    private String allowedOrigins;

    /** 关联的 AI 应用 ID */
    private Long appId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }

    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }
}
