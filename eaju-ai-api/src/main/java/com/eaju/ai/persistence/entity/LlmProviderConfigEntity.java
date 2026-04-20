package com.eaju.ai.persistence.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "llm_provider_config",
        indexes = {
                @Index(name = "idx_llm_provider_config_enabled_sort", columnList = "enabled,sort_order")
        }
)
public class LlmProviderConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "api_key", nullable = false, columnDefinition = "TEXT")
    private String apiKey = "";

    @Column(name = "base_url", nullable = false, columnDefinition = "TEXT")
    private String baseUrl;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** 非空时强制覆盖请求中的 temperature（如 Kimi 只接受 1.0） */
    @Column(name = "force_temperature", precision = 5, scale = 2)
    private BigDecimal forceTemperature;

    /** thinking 参数风格：openai（thinking.type）或 dashscope（enable_thinking） */
    @Column(name = "thinking_param_style", nullable = false, length = 32)
    private String thinkingParamStyle = "openai";

    /** 使用 JSON 模式时是否自动注入 json 关键词到 system message */
    @Column(name = "json_mode_system_hint", nullable = false)
    private boolean jsonModeSystemHint = false;

    /** 将历史 tool_calls 回传给模型前是否剥掉 index 字段（目前已全局剥掉，此列保留文档标记） */
    @Column(name = "strip_tool_call_index", nullable = false)
    private boolean stripToolCallIndex = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public BigDecimal getForceTemperature() { return forceTemperature; }
    public void setForceTemperature(BigDecimal forceTemperature) { this.forceTemperature = forceTemperature; }

    public String getThinkingParamStyle() { return thinkingParamStyle; }
    public void setThinkingParamStyle(String thinkingParamStyle) { this.thinkingParamStyle = thinkingParamStyle; }

    public boolean isJsonModeSystemHint() { return jsonModeSystemHint; }
    public void setJsonModeSystemHint(boolean jsonModeSystemHint) { this.jsonModeSystemHint = jsonModeSystemHint; }

    public boolean isStripToolCallIndex() { return stripToolCallIndex; }
    public void setStripToolCallIndex(boolean stripToolCallIndex) { this.stripToolCallIndex = stripToolCallIndex; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
