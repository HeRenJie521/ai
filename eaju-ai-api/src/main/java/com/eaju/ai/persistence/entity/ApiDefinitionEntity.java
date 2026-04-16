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

/**
 * 接口定义实体 - 用于管理可被工具调用的外部接口
 */
@Entity
@Table(name = "api_definition")
public class ApiDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 系统名称 */
    @Column(nullable = false, length = 256)
    private String systemName;

    /** 接口请求路径（完整 URL） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String requestUrl;

    /** 请求方式：GET/POST/PUT/DELETE/PATCH */
    @Column(name = "http_method", nullable = false, length = 16)
    private String httpMethod = "POST";

    /** 参数格式：application/json 或 application/x-www-form-urlencoded */
    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType = "application/json";

    /** 备注说明 */
    @Column(columnDefinition = "TEXT")
    private String remark;

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

    public String getSystemName() { return systemName; }
    public void setSystemName(String systemName) { this.systemName = systemName; }

    public String getRequestUrl() { return requestUrl; }
    public void setRequestUrl(String requestUrl) { this.requestUrl = requestUrl; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
