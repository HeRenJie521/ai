package com.eaju.ai.dto.auth;

import java.util.Map;

public class LoginResponseDto {

    private String token;
    /** 与 JWT jti 一致，用于登出时作废服务端会话缓存 */
    private String jti;
    /** 与 access token 一致的过期秒数（与 Redis 会话 TTL 对齐） */
    private long expiresIn;
    /** 与业务侧一致，一般为手机号 */
    private String userId;
    private String phone;
    private String username;
    private boolean admin;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getJti() {
        return jti;
    }

    public void setJti(String jti) {
        this.jti = jti;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    /** WEB_EMBED 登录时携带，用于前端聊天页默认选中该模型；普通登录为 null */
    private String defaultModel;

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    /** WEB_EMBED 登录时携带集成名称，用于前端展示 AI 助手名称；普通登录为 null */
    private String integrationName;

    public String getIntegrationName() {
        return integrationName;
    }

    public void setIntegrationName(String integrationName) {
        this.integrationName = integrationName;
    }

    /** 用户上下文字段缓存的 key-value，仅用于 appEmbedLogin 返回给前端测试展示 */
    private Map<String, Object> userContext;

    public Map<String, Object> getUserContext() {
        return userContext;
    }

    public void setUserContext(Map<String, Object> userContext) {
        this.userContext = userContext;
    }
}
