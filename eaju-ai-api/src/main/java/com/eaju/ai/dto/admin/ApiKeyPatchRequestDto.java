package com.eaju.ai.dto.admin;

public class ApiKeyPatchRequestDto {

    private String name;
    private Boolean enabled;

    /** WEB_EMBED 允许嵌入的来源域名，逗号分隔 */
    private String allowedOrigins;

    /**
     * 绑定的 AI 应用 ID（API_KEY 专属）。
     * 传 null：不修改；传 0：清除绑定；传正整数：绑定到对应应用。
     */
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
