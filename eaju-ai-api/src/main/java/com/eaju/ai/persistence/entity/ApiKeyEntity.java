package com.eaju.ai.persistence.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "api_key")
public class ApiKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "secret_hash", nullable = false, length = 64)
    private String secretHash;

    @Column(name = "secret_prefix", nullable = false, length = 64)
    private String secretPrefix;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * 集成类型：1=API_KEY（默认）  2=WEB_EMBED（嵌入网站）
     */
    @Column(nullable = false)
    private int type = 1;

    /** WEB_EMBED：默认对话模型 ID（如 deepseek-chat） */
    @Column(name = "default_model", length = 256)
    private String defaultModel;

    /** WEB_EMBED：允许嵌入的来源域名，逗号分隔；为空表示不限 */
    @Column(name = "allowed_origins", length = 1000)
    private String allowedOrigins;

    /** WEB_EMBED：开场白文本 */
    @Column(name = "welcome_text", columnDefinition = "TEXT")
    private String welcomeText;

    /** WEB_EMBED：推荐问题 JSON 字符串 */
    @Column(name = "suggestions", columnDefinition = "TEXT")
    private String suggestions;

    /** WEB_EMBED：Agent 角色设定 */
    @Column(name = "system_role", columnDefinition = "TEXT")
    private String systemRole;

    /** WEB_EMBED：Agent 任务指令 */
    @Column(name = "system_task", columnDefinition = "TEXT")
    private String systemTask;

    /** WEB_EMBED：Agent 限制条件 */
    @Column(name = "system_constraints", columnDefinition = "TEXT")
    private String systemConstraints;

    @PrePersist
    public void prePersist() {
        Instant n = Instant.now();
        if (createdAt == null) {
            createdAt = n;
        }
        updatedAt = n;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public void setSecretHash(String secretHash) {
        this.secretHash = secretHash;
    }

    public String getSecretPrefix() {
        return secretPrefix;
    }

    public void setSecretPrefix(String secretPrefix) {
        this.secretPrefix = secretPrefix;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public String getWelcomeText() {
        return welcomeText;
    }

    public void setWelcomeText(String welcomeText) {
        this.welcomeText = welcomeText;
    }

    public String getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(String suggestions) {
        this.suggestions = suggestions;
    }

    public String getSystemRole() {
        return systemRole;
    }

    public void setSystemRole(String systemRole) {
        this.systemRole = systemRole;
    }

    public String getSystemTask() {
        return systemTask;
    }

    public void setSystemTask(String systemTask) {
        this.systemTask = systemTask;
    }

    public String getSystemConstraints() {
        return systemConstraints;
    }

    public void setSystemConstraints(String systemConstraints) {
        this.systemConstraints = systemConstraints;
    }

}
