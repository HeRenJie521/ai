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
@Table(name = "ai_tool")
public class AiToolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 工具唯一名称（英文），用于 LLM function calling 的 name 字段 */
    @Column(nullable = false, unique = true, length = 128)
    private String name;

    /** 工具显示名 */
    @Column(nullable = false, length = 256)
    private String label;

    /** 工具功能描述，LLM 据此决定何时调用 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "http_method", nullable = false, length = 16)
    private String httpMethod = "POST";

    /** 请求 URL，支持 {{var}} 模板变量 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    /** 请求头 JSON 对象，支持 {{var}} 模板变量，如 {"Authorization": "Bearer {{token}}"} */
    @Column(name = "headers_json", columnDefinition = "TEXT")
    private String headersJson;

    /** 请求体模板，支持 {{var}} 模板变量（POST/PUT 时使用） */
    @Column(name = "body_template", columnDefinition = "TEXT")
    private String bodyTemplate;

    /** 参数格式：application/json 或 application/x-www-form-urlencoded */
    @Column(name = "content_type", length = 128)
    private String contentType = "application/json";

    /**
     * 传给 LLM 的 parameters JSON Schema，例如：
     * {"type":"object","properties":{"query":{"type":"string","description":"搜索关键词"}},"required":["query"]}
     */
    @Column(name = "params_schema_json", nullable = false, columnDefinition = "TEXT")
    private String paramsSchemaJson;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant n = Instant.now();
        if (createdAt == null) createdAt = n;
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

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getHeadersJson() { return headersJson; }
    public void setHeadersJson(String headersJson) { this.headersJson = headersJson; }

    public String getBodyTemplate() { return bodyTemplate; }
    public void setBodyTemplate(String bodyTemplate) { this.bodyTemplate = bodyTemplate; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getParamsSchemaJson() { return paramsSchemaJson; }
    public void setParamsSchemaJson(String paramsSchemaJson) { this.paramsSchemaJson = paramsSchemaJson; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
