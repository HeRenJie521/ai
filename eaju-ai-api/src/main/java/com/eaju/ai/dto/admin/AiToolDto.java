package com.eaju.ai.dto.admin;

public class AiToolDto {

    private Long id;
    private String name;
    private String label;
    private String description;
    /** 关联的接口定义 ID */
    private Long apiDefinitionId;
    /** 从关联的接口定义中获取的 URL（只读，用于前端展示） */
    private String url;
    /** 从关联的接口定义中获取的 HTTP 方法（只读，用于前端展示） */
    private String httpMethod;
    /** 从关联的接口定义中获取的 Content-Type（只读，用于前端展示） */
    private String contentType;
    private String headersJson;
    private String bodyTemplate;
    private String methodName;
    private String dataParamsJson;
    private String responseParamsJson;
    private String paramsSchemaJson;
    private boolean enabled;
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getApiDefinitionId() { return apiDefinitionId; }
    public void setApiDefinitionId(Long apiDefinitionId) { this.apiDefinitionId = apiDefinitionId; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getHeadersJson() { return headersJson; }
    public void setHeadersJson(String headersJson) { this.headersJson = headersJson; }

    public String getBodyTemplate() { return bodyTemplate; }
    public void setBodyTemplate(String bodyTemplate) { this.bodyTemplate = bodyTemplate; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public String getDataParamsJson() { return dataParamsJson; }
    public void setDataParamsJson(String dataParamsJson) { this.dataParamsJson = dataParamsJson; }

    public String getResponseParamsJson() { return responseParamsJson; }
    public void setResponseParamsJson(String responseParamsJson) { this.responseParamsJson = responseParamsJson; }

    public String getParamsSchemaJson() { return paramsSchemaJson; }
    public void setParamsSchemaJson(String paramsSchemaJson) { this.paramsSchemaJson = paramsSchemaJson; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
