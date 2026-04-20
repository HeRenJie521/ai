package com.eaju.ai.dto.admin;

public class AiAppUpdateRequestDto {

    private String name;
    private String welcomeText;
    private String suggestions;
    private String systemRole;
    private String systemTask;
    private String systemConstraints;
    /** 绑定的模型 ID（llm_model.id），null 表示不修改 */
    private Long llmModelId;

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

    public Long getLlmModelId() { return llmModelId; }
    public void setLlmModelId(Long llmModelId) { this.llmModelId = llmModelId; }
}
