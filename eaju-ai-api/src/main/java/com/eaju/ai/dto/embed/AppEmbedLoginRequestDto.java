package com.eaju.ai.dto.embed;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 应用管理嵌入登录请求 DTO。
 * <p>
 * 无需集成凭证校验，直接通过 appId 登录，JWT 中携带 appId claim。
 */
public class AppEmbedLoginRequestDto {

    @NotNull
    private Long appId;

    @NotBlank
    private String userId;

    private String username;

    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
