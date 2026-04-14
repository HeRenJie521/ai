package com.eaju.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;

public class ChatResponseDto {

    /** 与请求 provider 一致，使用库表中的规范 code */
    private String provider;
    private String model;
    private String id;
    private String content;
    /** DeepSeek 思考模式等场景下，模型思维链（若上游返回） */
    private String reasoningContent;
    private String finishReason;
    private JsonNode usage;
    private JsonNode raw;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getReasoningContent() {
        return reasoningContent;
    }

    public void setReasoningContent(String reasoningContent) {
        this.reasoningContent = reasoningContent;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    public JsonNode getUsage() {
        return usage;
    }

    public void setUsage(JsonNode usage) {
        this.usage = usage;
    }

    public JsonNode getRaw() {
        return raw;
    }

    public void setRaw(JsonNode raw) {
        this.raw = raw;
    }
}
