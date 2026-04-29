package com.eaju.ai.dto;

public class UserAiAppDto {

    private Long id;
    private String name;
    /** 前端展示用：提供商名称·模型名称，如"通义千问·qwen3.5-plus" */
    private String modelDisplayName;
    /** 发往 /api/chat 的 provider 字段（llm_provider_config.code） */
    private String providerCode;
    /** 发往 /api/chat 的 mode 字段（llm_model.name，即 modes_json 逻辑名） */
    private String modeKey;
    private boolean deepThinking;
    private boolean vision;
    private boolean streamOutput;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getModelDisplayName() { return modelDisplayName; }
    public void setModelDisplayName(String modelDisplayName) { this.modelDisplayName = modelDisplayName; }

    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }

    public String getModeKey() { return modeKey; }
    public void setModeKey(String modeKey) { this.modeKey = modeKey; }

    public boolean isDeepThinking() { return deepThinking; }
    public void setDeepThinking(boolean deepThinking) { this.deepThinking = deepThinking; }

    public boolean isVision() { return vision; }
    public void setVision(boolean vision) { this.vision = vision; }

    public boolean isStreamOutput() { return streamOutput; }
    public void setStreamOutput(boolean streamOutput) { this.streamOutput = streamOutput; }
}
