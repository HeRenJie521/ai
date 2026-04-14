package com.eaju.ai.dto.admin;

import javax.validation.constraints.NotBlank;

public class ApiKeyCreateRequestDto {

    @NotBlank
    private String name;

    /**
     * 集成类型：1=API_KEY（默认）  2=WEB_EMBED
     */
    private int type = 1;

    /** WEB_EMBED 专属：默认对话模型（如 deepseek-chat），type=2 时必填 */
    private String defaultModel;

    /** WEB_EMBED 专属：允许嵌入的来源域名，逗号分隔，为空表示不限 */
    private String allowedOrigins;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
}
