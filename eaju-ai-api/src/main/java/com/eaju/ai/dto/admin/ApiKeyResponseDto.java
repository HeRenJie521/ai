package com.eaju.ai.dto.admin;

public class ApiKeyResponseDto {

    private Long id;
    private String name;
    private String secretPrefix;
    private boolean enabled;
    private String createdAt;

    /** 1=API_KEY  2=WEB_EMBED */
    private int type = 1;

    /** WEB_EMBED 允许嵌入的来源域名 */
    private String allowedOrigins;

    /** 绑定的 AI 应用 ID */
    private Long appId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSecretPrefix() { return secretPrefix; }
    public void setSecretPrefix(String secretPrefix) { this.secretPrefix = secretPrefix; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    public String getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }

    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }
}
