package com.eaju.ai.dto.admin;

import javax.validation.constraints.NotBlank;

public class AiAppCreateRequestDto {

    @NotBlank
    private String name;

    private String welcomeText;
    private String suggestions;
    private String systemRole;
    private String systemTask;
    private String systemConstraints;
    private String modelId;
    private Long modelProviderId;

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

    public Long getModelProviderId() { return modelProviderId; }
    public void setModelProviderId(Long modelProviderId) { this.modelProviderId = modelProviderId; }
}
