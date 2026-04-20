package com.eaju.ai.dto.admin;

public class AiAppResponseDto {

    private Long id;
    private String name;
    private String welcomeText;
    private String suggestions;
    private String systemRole;
    private String systemTask;
    private String systemConstraints;
    /** 绑定的模型 ID（llm_model.id） */
    private Long llmModelId;
    /** 前端展示用：提供商名称·模型名称，如"通义千问·qwen3.5-plus" */
    private String modelDisplayName;
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public String getModelDisplayName() { return modelDisplayName; }
    public void setModelDisplayName(String modelDisplayName) { this.modelDisplayName = modelDisplayName; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
