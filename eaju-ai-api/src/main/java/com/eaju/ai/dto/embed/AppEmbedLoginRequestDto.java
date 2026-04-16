package com.eaju.ai.dto.embed;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Map;

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

    /**
     * 额外用户上下文字段（可选），如 {"department": "研发部", "employeeId": "E001"}。
     * 字段 key 须在 user_context_field 表中配置为 enabled=true 才会被存入缓存。
     */
    private Map<String, Object> extraContext;

    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Map<String, Object> getExtraContext() { return extraContext; }
    public void setExtraContext(Map<String, Object> extraContext) { this.extraContext = extraContext; }
}
