package com.eaju.ai.llm.support;

import com.eaju.ai.dto.ResponseFormatKind;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 库表 {@code inference_defaults_json} 或原 yaml 中 {@code inference-defaults} 的可选默认推理参数。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InferenceDefaults {

    private Double temperature;
    private Integer maxTokens;
    private Double topP;
    private Integer topK;
    /** 对应 OpenAI 的 {@code n}，生成候选条数 */
    private Integer sampleCount;
    private Double frequencyPenalty;
    private Double presencePenalty;
    private ResponseFormatKind responseFormat;
    /** DeepSeek：{@code thinking.type=enabled}，其它厂商当前忽略 */
    private Boolean thinkingMode;
    /**
     * 非空时覆盖基于 code/URL 的推断：该提供方是否按 OpenAI 兼容形态下发 {@code thinking}（百炼/DeepSeek 等）。
     * 为空则由服务端按提供方 code、Base URL 自动识别。
     */
    /** OpenAI {@code stop}：字符串序列，遇其停止生成 */
    private List<String> stop;

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

    public List<String> getStop() {
        return stop;
    }

    public void setStop(List<String> stop) {
        this.stop = stop;
    }
}
