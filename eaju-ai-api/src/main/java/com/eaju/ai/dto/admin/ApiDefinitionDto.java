package com.eaju.ai.dto.admin;

import java.time.Instant;

/**
 * 接口定义 DTO
 */
public class ApiDefinitionDto {

    private Long id;
    private String systemName;
    private String requestUrl;
    private String httpMethod;
    private String contentType;
    private String remark;
    private String createdAt;
    private String updatedAt;

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

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    /** 从实体转换为 DTO */
    public static ApiDefinitionDto fromEntity(com.eaju.ai.persistence.entity.ApiDefinitionEntity entity) {
        if (entity == null) return null;
        ApiDefinitionDto dto = new ApiDefinitionDto();
        dto.setId(entity.getId());
        dto.setSystemName(entity.getSystemName());
        dto.setRequestUrl(entity.getRequestUrl());
        dto.setHttpMethod(entity.getHttpMethod());
        dto.setContentType(entity.getContentType());
        dto.setRemark(entity.getRemark());
        dto.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        dto.setUpdatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        return dto;
    }
}
