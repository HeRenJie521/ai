package com.eaju.ai.persistence.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "chat_conversation",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "session_id"}),
        indexes = @Index(name = "idx_chat_conversation_user_last", columnList = "user_id,last_message_at")
)
public class ChatConversationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务用户标识：登录接口返回的手机号 */
    @Column(name = "user_id", nullable = false, length = 32)
    private String userId;

    @Column(name = "session_id", nullable = false, length = 128)
    private String sessionId;

    @Column(nullable = false, length = 200)
    private String title = "新对话";

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_provider_code", length = 64)
    private String lastProviderCode;

    @Column(name = "last_mode_key", length = 512)
    private String lastModeKey;

    @Column(name = "api_key_id")
    private Long apiKeyId;

    /** WEB_EMBED 集成 ID（api_key.id），用于统计该集成的对话用量；普通会话为 null */
    @Column(name = "integration_id")
    private Long integrationId;

    /** 逻辑删除时间，非空表示已删除 */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (lastMessageAt == null) {
            lastMessageAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(Instant lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
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

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Long getIntegrationId() {
        return integrationId;
    }

    public void setIntegrationId(Long integrationId) {
        this.integrationId = integrationId;
    }
}
