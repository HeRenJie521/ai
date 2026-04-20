package com.eaju.ai.dto.conversation;

public class ConversationResponseDto {

    private String sessionId;
    private String title;
    private String lastMessageAt;
    /** 该会话最近一次发消息选用的提供方 code，可为空 */
    private String lastProviderCode;
    /** 该会话最近一次发消息选用的 mode 键，可为空 */
    private String lastModeKey;
    /** 前端展示用：提供商名称·模型名称，如"通义千问·qwen3.5-plus" */
    private String lastModelDisplayName;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(String lastMessageAt) { this.lastMessageAt = lastMessageAt; }

    public String getLastProviderCode() { return lastProviderCode; }
    public void setLastProviderCode(String lastProviderCode) { this.lastProviderCode = lastProviderCode; }

    public String getLastModeKey() { return lastModeKey; }
    public void setLastModeKey(String lastModeKey) { this.lastModeKey = lastModeKey; }

    public String getLastModelDisplayName() { return lastModelDisplayName; }
    public void setLastModelDisplayName(String lastModelDisplayName) { this.lastModelDisplayName = lastModelDisplayName; }
}
