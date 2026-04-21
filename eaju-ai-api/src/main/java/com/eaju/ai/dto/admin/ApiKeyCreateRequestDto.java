package com.eaju.ai.dto.admin;

import javax.validation.constraints.NotBlank;

public class ApiKeyCreateRequestDto {

    @NotBlank
    private String name;

    /**
     * 集成类型：1=API_KEY（默认）  2=WEB_EMBED
     */
    private int type = 1;

    /** 允许嵌入的来源域名，逗号分隔，为空表示不限（WEB_EMBED 专属） */
    private String allowedOrigins;

    /** 绑定的 AI 应用 ID（API_KEY 专属，可选） */
    private Long appId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    public String getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }

    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }
}
