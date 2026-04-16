package com.eaju.ai.dto.admin;

import javax.validation.constraints.NotBlank;

public class AiToolSaveRequestDto {

    @NotBlank
    private String name;

    private String label;

    @NotBlank
    private String description;

    private String httpMethod;

    @NotBlank
    private String url;

    private String headersJson;

    private String bodyTemplate;

    private String contentType;

    /** DMS 接口方法名，如 miniAppMenuFunctionQuery */
    private String methodName;

    /**
     * data 对象参数配置，JSON 数组：
     * [{"key":"userId","valueType":"context","fieldKey":"esusMobile"},
     *  {"key":"functionName","valueType":"static","value":"智蚁轨迹"}]
     */
    private String dataParamsJson;

    private String responseParamsJson;

    @NotBlank
    private String paramsSchemaJson;

    private Boolean enabled;

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

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public String getDataParamsJson() { return dataParamsJson; }
    public void setDataParamsJson(String dataParamsJson) { this.dataParamsJson = dataParamsJson; }

    public String getResponseParamsJson() { return responseParamsJson; }
    public void setResponseParamsJson(String responseParamsJson) { this.responseParamsJson = responseParamsJson; }

    public String getParamsSchemaJson() { return paramsSchemaJson; }
    public void setParamsSchemaJson(String paramsSchemaJson) { this.paramsSchemaJson = paramsSchemaJson; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
