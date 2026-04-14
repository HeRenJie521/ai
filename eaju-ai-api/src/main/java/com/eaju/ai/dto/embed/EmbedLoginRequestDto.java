package com.eaju.ai.dto.embed;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 嵌入网站免密 SSO 登录请求体。
 *
 * <p>调用方（集成方后端）生成 iframe URL 时只需传入：
 * <pre>
 *   iid   = 集成 ID
 *   uid   = 用户手机号
 *   token = 该集成的 Embed Token（从管理后台复制）
 * </pre>
 */
public class EmbedLoginRequestDto {

    /** 集成 ID（api_key.id，type=2） */
    @NotNull
    private Long integrationId;

    /** 业务用户 ID（手机号） */
    @NotBlank
    private String userId;

    /** 嵌入凭证（Embed Token，与管理台展示的一致） */
    @NotBlank
    private String token;

    public Long getIntegrationId() {
        return integrationId;
    }

    public void setIntegrationId(Long integrationId) {
        this.integrationId = integrationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
