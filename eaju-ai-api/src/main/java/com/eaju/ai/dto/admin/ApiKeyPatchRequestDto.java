package com.eaju.ai.dto.admin;

public class ApiKeyPatchRequestDto {

    private String name;
    private Boolean enabled;

    /** WEB_EMBED 默认对话模型 */
    private String defaultModel;

    /** WEB_EMBED 允许嵌入的来源域名，逗号分隔 */
    private String allowedOrigins;

    /** WEB_EMBED 开场白文本 */
    private String welcomeText;
    
    /** WEB_EMBED 推荐问题 JSON 字符串 */
    private String suggestions;

    private String systemRole;
    private String systemTask;
    private String systemConstraints;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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
