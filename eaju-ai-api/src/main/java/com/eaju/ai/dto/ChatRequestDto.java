package com.eaju.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

public class ChatRequestDto {

    /**
     * 与表 {@code llm_provider_config.code} 对应（大小写不敏感），如 DEEPSEEK、QWEN。
     */
    @NotBlank
    private String provider;

    /**
     * 会话 ID（可选）：有值时从 Redis 读取该会话历史并拼在本轮 {@link #messages} 之前，阻塞对话成功后再写回。
     */
    private String sessionId;

    /** 业务用户 ID（可选），写入 chat_turn 便于关联业务账号 */
    private String userId;

    /**
     * 选择库表 {@code modes_json} 中的逻辑名；不传则使用该提供方的 {@code default_mode}。
     * 与 {@link #model} 二选一优先：若 {@code model} 有值则直接作为上游 model id，忽略 mode 映射。
     */
    private String mode;

    /**
     * 直接指定上游 model id，绕过 mode 映射。
     */
    private String model;

    @Valid
    @NotEmpty
    private List<ChatMessageDto> messages;

    // ---------- 推理 / 采样参数（均可选；未传则用库表 inference_defaults_json）----------

    /** 温度 */
    private Double temperature;
    /** 最大输出 token（OpenAI 字段 max_tokens） */
    private Integer maxTokens;
    /** Top P 核采样 */
    private Double topP;
    /** Top K（部分厂商支持，如通义、Gemini 兼容层） */
    private Integer topK;
    /** 取样数量：对应 OpenAI 的 n（生成多少条候选 completion） */
    private Integer sampleCount;
    /** 频率惩罚 */
    private Double frequencyPenalty;
    /** 重复惩罚 / 存在惩罚 */
    private Double presencePenalty;
    /** 回复格式：TEXT 或 JSON_OBJECT */
    private ResponseFormatKind responseFormat;
    /**
     * 思考模式：当前对 DeepSeek 生效，请求体带 {@code thinking: {type: enabled}}；
     * 其它厂商若不支持该字段会被上游忽略或报错，请按需关闭或扩展子类。
     */
    private Boolean thinkingMode;

    /**
     * {@code false} 或未传：阻塞式，一次返回完整 JSON；{@code true}：流式，响应为 {@code text/event-stream}（SSE）。
     */
    private Boolean stream;

    /** 服务端写入：X-API-Key 调用时的密钥 id，不落库到请求 JSON */
    @JsonIgnore
    private Long internalApiKeyId;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<ChatMessageDto> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessageDto> messages) {
        this.messages = messages;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Integer getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(Integer sampleCount) {
        this.sampleCount = sampleCount;
    }

    public Double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public void setFrequencyPenalty(Double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
    }

    public Double getPresencePenalty() {
        return presencePenalty;
    }

    public void setPresencePenalty(Double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    public ResponseFormatKind getResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(ResponseFormatKind responseFormat) {
        this.responseFormat = responseFormat;
    }

    public Boolean getThinkingMode() {
        return thinkingMode;
    }

    public void setThinkingMode(Boolean thinkingMode) {
        this.thinkingMode = thinkingMode;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public Long getInternalApiKeyId() {
        return internalApiKeyId;
    }

    public void setInternalApiKeyId(Long internalApiKeyId) {
        this.internalApiKeyId = internalApiKeyId;
    }
}
