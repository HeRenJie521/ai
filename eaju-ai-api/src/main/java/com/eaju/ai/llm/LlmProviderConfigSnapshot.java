package com.eaju.ai.llm;

import com.eaju.ai.dto.ChatRequestDto;
import com.eaju.ai.dto.ResponseFormatKind;
import com.eaju.ai.dto.llm.ModeCapabilityDto;
import com.eaju.ai.llm.support.InferenceDefaults;
import com.eaju.ai.persistence.entity.LlmModelEntity;
import com.eaju.ai.persistence.entity.LlmProviderConfigEntity;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 {@link LlmProviderConfigEntity} 和对应的 {@link LlmModelEntity} 列表构建的运行时只读配置。
 */
public final class LlmProviderConfigSnapshot {

    private final String code;
    private final String displayName;
    private final String apiKey;
    private final String baseUrl;
    private final String defaultMode;
    private final Map<String, ModeEntryConfig> modeEntries;
    private final Map<String, String> modesFlat;

    /** 非空时强制覆盖请求中的 temperature（如 Kimi 只接受 1.0） */
    private final Double forceTemperature;
    /** thinking 参数风格：openai 或 dashscope */
    private final String thinkingParamStyle;
    /** 使用 JSON 模式时是否自动注入 json 关键词 */
    private final boolean jsonModeSystemHint;

    private LlmProviderConfigSnapshot(
            String code, String displayName, String apiKey, String baseUrl, String defaultMode,
            Map<String, ModeEntryConfig> modeEntries,
            Double forceTemperature, String thinkingParamStyle, boolean jsonModeSystemHint) {
        this.code = code;
        this.displayName = displayName;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.defaultMode = defaultMode;
        this.modeEntries = modeEntries != null ? modeEntries : Collections.emptyMap();
        this.modesFlat = buildFlat(this.modeEntries);
        this.forceTemperature = forceTemperature;
        this.thinkingParamStyle = thinkingParamStyle;
        this.jsonModeSystemHint = jsonModeSystemHint;
    }

    public static LlmProviderConfigSnapshot fromEntityAndModels(
            LlmProviderConfigEntity provider, List<LlmModelEntity> models) {

        LinkedHashMap<String, ModeEntryConfig> entries = new LinkedHashMap<>();
        String firstModeName = null;

        for (LlmModelEntity model : models) {
            if (!model.isEnabled()) continue;
            String name = model.getName();
            if (name == null || name.trim().isEmpty()) continue;
            if (firstModeName == null) firstModeName = name;

            InferenceDefaults inf = buildInferenceDefaults(model);
            ModeEntryConfig entry = new ModeEntryConfig(
                    model.getId(),
                    model.getUpstreamModelId(),
                    model.isTextGeneration(),
                    model.isDeepThinking(),
                    model.isVision(),
                    model.isStreamOutput(),
                    model.isToolCall(),
                    model.isForceThinkingEnabled(),
                    model.getContextWindow(),
                    inf);
            entries.put(name, entry);
        }

        Double forceTmp = provider.getForceTemperature() != null
                ? provider.getForceTemperature().doubleValue() : null;
        String style = StringUtils.hasText(provider.getThinkingParamStyle())
                ? provider.getThinkingParamStyle().trim() : "openai";

        return new LlmProviderConfigSnapshot(
                provider.getCode() != null ? provider.getCode().trim() : "",
                provider.getDisplayName() != null ? provider.getDisplayName().trim() : "",
                provider.getApiKey() != null ? provider.getApiKey() : "",
                provider.getBaseUrl() != null ? provider.getBaseUrl().trim() : "",
                firstModeName != null ? firstModeName : "",
                entries,
                forceTmp, style, provider.isJsonModeSystemHint());
    }

    private static InferenceDefaults buildInferenceDefaults(LlmModelEntity model) {
        InferenceDefaults inf = new InferenceDefaults();
        if (model.getTemperature() != null) inf.setTemperature(model.getTemperature().doubleValue());
        inf.setMaxTokens(model.getMaxTokens());
        if (model.getTopP() != null) inf.setTopP(model.getTopP().doubleValue());
        inf.setTopK(model.getTopK());
        if (model.getFrequencyPenalty() != null) inf.setFrequencyPenalty(model.getFrequencyPenalty().doubleValue());
        if (model.getPresencePenalty() != null) inf.setPresencePenalty(model.getPresencePenalty().doubleValue());
        if (StringUtils.hasText(model.getResponseFormat())) {
            try {
                inf.setResponseFormat(ResponseFormatKind.valueOf(model.getResponseFormat().toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }
        inf.setThinkingMode(model.getThinkingMode());
        return inf;
    }

    private static Map<String, String> buildFlat(Map<String, ModeEntryConfig> entries) {
        LinkedHashMap<String, String> flat = new LinkedHashMap<>();
        for (Map.Entry<String, ModeEntryConfig> en : entries.entrySet()) {
            flat.put(en.getKey(), en.getValue().getUpstreamModelId());
        }
        return flat;
    }

    public void validateOrThrow() {
        String label = StringUtils.hasText(displayName) ? displayName : code;
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException(label + " 未配置 api-key");
        }
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException(label + " 未配置 base-url");
        }
        if (modeEntries.isEmpty()) {
            throw new IllegalStateException(label + " 未配置任何模型，请在「模型管理」中为该提供商新增模型");
        }
    }

    public String resolveUpstreamModelId(String explicitModel, String modeKeyOrNull) {
        if (StringUtils.hasText(explicitModel)) {
            return normalizeUpstreamModelId(explicitModel.trim());
        }
        String modeKey = StringUtils.hasText(modeKeyOrNull) ? modeKeyOrNull.trim() : defaultMode.trim();
        ModeEntryConfig entry = findEntry(modeKey);
        if (entry == null || !StringUtils.hasText(entry.getUpstreamModelId())) {
            throw new IllegalArgumentException(
                    (StringUtils.hasText(displayName) ? displayName : code)
                            + " 未知 mode: " + modeKey
                            + "，可用: " + modeEntries.keySet());
        }
        return normalizeUpstreamModelId(entry.getUpstreamModelId());
    }

    private static String normalizeUpstreamModelId(String raw) {
        if (!StringUtils.hasText(raw)) return raw;
        String s = raw.trim();
        String stripped = s.replaceFirst("\\s*\\([^)]*\\)\\s*$", "").trim();
        return stripped.isEmpty() ? s : stripped;
    }

    public ModeEntryConfig findEntry(String modeKey) {
        if (!StringUtils.hasText(modeKey)) return null;
        if (modeEntries.containsKey(modeKey)) return modeEntries.get(modeKey);
        for (Map.Entry<String, ModeEntryConfig> en : modeEntries.entrySet()) {
            if (en.getKey() != null && en.getKey().equalsIgnoreCase(modeKey.trim())) {
                return en.getValue();
            }
        }
        return null;
    }

    public InferenceDefaults getModeDefaults(String modeKey) {
        ModeEntryConfig entry = findEntry(modeKey);
        if (entry != null) return entry.getInferenceDefaults();
        if (!modeEntries.isEmpty()) {
            return modeEntries.values().iterator().next().getInferenceDefaults();
        }
        return new InferenceDefaults();
    }

    public boolean modeSupportsDeepThinking(String modeKey) {
        ModeEntryConfig e = findEntry(modeKey);
        return e != null && e.isDeepThinking();
    }

    public boolean modeSupportsToolCall(String modeKey) {
        ModeEntryConfig e = findEntry(modeKey);
        return e == null || e.isToolCall();
    }

    public boolean modeSupportsStreamOutput(String modeKey) {
        ModeEntryConfig e = findEntry(modeKey);
        return e == null || e.isStreamOutput();
    }

    public boolean modeSupportsVision(String modeKey) {
        ModeEntryConfig e = findEntry(modeKey);
        // 未配置 modeCapabilities 时视为兼容旧数据，不拦截
        return e == null || e.isVision();
    }

    public boolean modeSupportsThinkingApi(String modeKey) {
        ModeEntryConfig e = findEntry(modeKey);
        return e != null && e.isDeepThinking();
    }

    public boolean modeForceThinkingEnabled(String modeKey) {
        ModeEntryConfig e = findEntry(modeKey);
        return e != null && e.isForceThinkingEnabled();
    }

    public Long getModeModelId(String modeKey) {
        ModeEntryConfig e = findEntry(modeKey);
        return e != null ? e.getModelId() : null;
    }

    public boolean resolveThinkingContentWanted(ChatRequestDto request) {
        if (request == null) return false;
        String modeKey = resolveEffectiveModeKey(request);
        Boolean fromReq = request.getThinkingMode();
        InferenceDefaults d = getModeDefaults(modeKey);
        boolean toggleOn = fromReq != null
                ? Boolean.TRUE.equals(fromReq)
                : Boolean.TRUE.equals(d.getThinkingMode());
        if (!toggleOn) return false;
        return modeSupportsThinkingApi(modeKey) && modeSupportsDeepThinking(modeKey);
    }

    private String resolveEffectiveModeKey(ChatRequestDto request) {
        if (request != null && StringUtils.hasText(request.getMode())) {
            return request.getMode().trim();
        }
        return defaultMode != null ? defaultMode.trim() : "";
    }

    public Map<String, ModeCapabilityDto> buildModeCapabilitiesDto() {
        LinkedHashMap<String, ModeCapabilityDto> out = new LinkedHashMap<>();
        for (Map.Entry<String, ModeEntryConfig> en : modeEntries.entrySet()) {
            ModeCapabilityDto d = new ModeCapabilityDto();
            d.setTextGeneration(en.getValue().isTextGeneration());
            d.setDeepThinking(en.getValue().isDeepThinking());
            d.setVision(en.getValue().isVision());
            d.setStreamOutput(en.getValue().isStreamOutput());
            d.setToolCall(en.getValue().isToolCall());
            d.setContextWindow(en.getValue().getContextWindow());
            out.put(en.getKey(), d);
        }
        return out;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public String getApiKey() { return apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public String getDefaultMode() { return defaultMode; }
    public Double getForceTemperature() { return forceTemperature; }
    public String getThinkingParamStyle() { return thinkingParamStyle; }
    public boolean isJsonModeSystemHint() { return jsonModeSystemHint; }
    public boolean usesDashScopeThinkingParam() { return "dashscope".equalsIgnoreCase(thinkingParamStyle); }

    /** 逻辑名 → 上游 model id（兼容旧接口） */
    public Map<String, String> getModes() { return modesFlat; }
}
