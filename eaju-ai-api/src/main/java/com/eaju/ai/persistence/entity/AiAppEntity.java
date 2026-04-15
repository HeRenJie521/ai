package com.eaju.ai.persistence.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ai_app")
public class AiAppEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    /** 开场白文本 */
    @Column(name = "welcome_text", columnDefinition = "TEXT")
    private String welcomeText;

    /** 推荐问题 JSON 字符串 */
    @Column(name = "suggestions", columnDefinition = "TEXT")
    private String suggestions;

    /** Agent 角色设定 */
    @Column(name = "system_role", columnDefinition = "TEXT")
    private String systemRole;

    /** Agent 任务指令 */
    @Column(name = "system_task", columnDefinition = "TEXT")
    private String systemTask;

    /** Agent 限制条件 */
    @Column(name = "system_constraints", columnDefinition = "TEXT")
    private String systemConstraints;

    /** 默认对话模型 ID */
    @Column(name = "model_id", length = 256)
    private String modelId;

    /** 采样温度，为空时使用模型默认值 */
    @Column(name = "temperature", precision = 4, scale = 2)
    private BigDecimal temperature;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getWelcomeText() { return welcomeText; }
    public void setWelcomeText(String welcomeText) { this.welcomeText = welcomeText; }

    public String getSuggestions() { return suggestions; }
    public void setSuggestions(String suggestions) { this.suggestions = suggestions; }

    public String getSystemRole() { return systemRole; }
    public void setSystemRole(String systemRole) { this.systemRole = systemRole; }

    public String getSystemTask() { return systemTask; }
    public void setSystemTask(String systemTask) { this.systemTask = systemTask; }

    public String getSystemConstraints() { return systemConstraints; }
    public void setSystemConstraints(String systemConstraints) { this.systemConstraints = systemConstraints; }

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }

    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
