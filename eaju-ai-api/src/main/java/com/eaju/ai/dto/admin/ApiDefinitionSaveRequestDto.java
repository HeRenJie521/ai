package com.eaju.ai.dto.admin;

import javax.validation.constraints.NotBlank;

/**
 * 接口定义保存请求 DTO
 */
public class ApiDefinitionSaveRequestDto {

    @NotBlank
    private String systemName;

    @NotBlank
    private String requestUrl;

    private String httpMethod;

    @NotBlank
    private String contentType;

    private String remark;

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
}
