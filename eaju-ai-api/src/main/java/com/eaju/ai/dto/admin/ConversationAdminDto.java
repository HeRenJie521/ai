package com.eaju.ai.dto.admin;

public class ConversationAdminDto {

    private Long id;
    private String sessionId;
    private String userId;
    private String title;
    private String lastMessageAt;
    private String createdAt;
    private String lastProviderCode;
    private String lastModeKey;
    private Long apiKeyId;
    private String apiKeyName;
    private String deletedAt;
    private Long turnCount;
    private Long totalTokens;
    /** 会话类型：CHAT-用户聊天，API_KEY-API Key调用 */
    private String type;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(String lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getLastProviderCode() {
        return lastProviderCode;
    }

    public void setLastProviderCode(String lastProviderCode) {
        this.lastProviderCode = lastProviderCode;
    }

    public String getLastModeKey() {
        return lastModeKey;
    }

    public void setLastModeKey(String lastModeKey) {
        this.lastModeKey = lastModeKey;
    }

    public Long getApiKeyId() {
        return apiKeyId;
    }

    public void setApiKeyId(Long apiKeyId) {
        this.apiKeyId = apiKeyId;
    }

    public String getApiKeyName() {
        return apiKeyName;
    }

    public void setApiKeyName(String apiKeyName) {
        this.apiKeyName = apiKeyName;
    }

    public String getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(String deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Long getTurnCount() {
        return turnCount;
    }

    public void setTurnCount(Long turnCount) {
        this.turnCount = turnCount;
    }

    public Long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Long totalTokens) {
        this.totalTokens = totalTokens;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}