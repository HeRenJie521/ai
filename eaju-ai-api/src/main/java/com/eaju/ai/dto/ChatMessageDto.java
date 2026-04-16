package com.eaju.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotBlank;
import java.util.List;

public class ChatMessageDto {

    @NotBlank
    private String role;

    /**
     * 用户文本；可与 {@link #fileUrls} 同时存在。纯附件时可为空串。
     */
    private String content;

    /** 可选：DeepSeek 等思考过程，仅部分接口序列化 */
    private String reasoningContent;

    /**
     * 经 eaju-open 上传后的公网 URL 列表；图片会按 OpenAI vision 形态发给上游，其它类型以文本链接触达模型。
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> fileUrls;

    /** 消息时间（ISO-8601），仅管理端消息查看接口返回，不传给上游模型 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String createdAt;

    /** tool 角色消息时对应的 tool_call_id */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String toolCallId;

    /** assistant 消息中 LLM 发出的 tool_calls JSON 数组字符串（内部使用，不序列化给前端） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String toolCallsJson;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
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

    public List<String> getFileUrls() {
        return fileUrls;
    }

    public void setFileUrls(List<String> fileUrls) {
        this.fileUrls = fileUrls;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getToolCallsJson() {
        return toolCallsJson;
    }

    public void setToolCallsJson(String toolCallsJson) {
        this.toolCallsJson = toolCallsJson;
    }
}
