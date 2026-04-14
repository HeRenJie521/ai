package com.eaju.ai.dto.admin;

import java.util.List;

public class ConversationDetailDto {

    private String sessionId;
    private String userId;
    private String title;
    private String createdAt;
    private String lastMessageAt;
    private String lastProviderCode;
    private String lastModeKey;
    private Long apiKeyId;
    private String apiKeyName;
    private String deletedAt;
    private TokenUsageDto usage;
    private List<ModelUsageRowDto> byModel;
    /** 会话类型：CHAT-用户聊天，API_KEY-API Key调用 */
    private String type;

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

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(String lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
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

    public TokenUsageDto getUsage() {
        return usage;
    }

    public void setUsage(TokenUsageDto usage) {
        this.usage = usage;
    }

    public List<ModelUsageRowDto> getByModel() {
        return byModel;
    }

    public void setByModel(List<ModelUsageRowDto> byModel) {
        this.byModel = byModel;
    }

    public static class TokenUsageDto {
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;

        public long getPromptTokens() {
            return promptTokens;
        }

        public void setPromptTokens(long promptTokens) {
            this.promptTokens = promptTokens;
        }

        public long getCompletionTokens() {
            return completionTokens;
        }

        public void setCompletionTokens(long completionTokens) {
            this.completionTokens = completionTokens;
        }

        public long getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(long totalTokens) {
            this.totalTokens = totalTokens;
        }
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}