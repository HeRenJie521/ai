package com.eaju.ai.llm;

import com.eaju.ai.dto.ChatRequestDto;
import com.eaju.ai.dto.llm.ModeCapabilityDto;
import com.eaju.ai.llm.support.InferenceDefaults;
import com.eaju.ai.persistence.entity.LlmProviderConfigEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 从 {@link LlmProviderConfigEntity} 解析出的运行时只读配置。
 */
public final class LlmProviderConfigSnapshot {

    private final String code;
    private final String displayName;
    private final String apiKey;
    private final String baseUrl;
    private final String defaultMode;
    private final Map<String, ModeEntryConfig> modeEntries;
    private final Map<String, String> modesFlat;
    private final InferenceDefaults inferenceDefaults;

    private LlmProviderConfigSnapshot(
            String code,
            String displayName,
            String apiKey,
            String baseUrl,
            String defaultMode,
            Map<String, ModeEntryConfig> modeEntries,
            InferenceDefaults inferenceDefaults) {
        this.code = code;
        this.displayName = displayName;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.defaultMode = defaultMode;
        this.modeEntries = modeEntries != null ? modeEntries : Collections.emptyMap();
        this.modesFlat = flattenModes(this.modeEntries);
        this.inferenceDefaults = inferenceDefaults;
    }

    public static LlmProviderConfigSnapshot fromEntity(LlmProviderConfigEntity e, ObjectMapper objectMapper)
            throws JsonProcessingException {
        InferenceDefaults inf = new InferenceDefaults();
        if (StringUtils.hasText(e.getInferenceDefaultsJson())) {
            inf = objectMapper.readValue(e.getInferenceDefaultsJson().trim(), InferenceDefaults.class);
        }
        String canonicalCode = e.getCode() != null ? e.getCode().trim() : "";
        String baseUrlTrim = e.getBaseUrl() != null ? e.getBaseUrl().trim() : "";
        boolean legacyDeepDef = inferGatewayThinkingSupport(canonicalCode, baseUrlTrim);
        Map<String, ModeEntryConfig> entries = ModesJsonParser.parse(e.getModesJson().trim(), legacyDeepDef);
        return new LlmProviderConfigSnapshot(
                canonicalCode,
                e.getDisplayName() != null ? e.getDisplayName().trim() : canonicalCode,
                e.getApiKey() != null ? e.getApiKey() : "",
                baseUrlTrim,
                e.getDefaultMode() != null ? e.getDefaultMode().trim() : "",
                entries,
                inf != null ? inf : new InferenceDefaults()
        );
    }

    private static Map<String, String> flattenModes(Map<String, ModeEntryConfig> entries) {
        LinkedHashMap<String, String> flat = new LinkedHashMap<String, String>();
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
            throw new IllegalStateException(label + " 未配置 modes（至少一组 逻辑名: 上游model）");
        }
        if (!StringUtils.hasText(defaultMode)) {
            throw new IllegalStateException(label + " 未配置 default-mode");
        }
        if (resolveModeKeyToModelId(defaultMode.trim()) == null) {
            throw new IllegalStateException(label + " 的 default-mode 在 modes 中不存在: " + defaultMode);
        }
    }

    public String resolveUpstreamModelId(String explicitModel, String modeKeyOrNull) {
        if (StringUtils.hasText(explicitModel)) {
            return normalizeUpstreamModelId(explicitModel.trim());
        }
        String modeKey = StringUtils.hasText(modeKeyOrNull)
                ? modeKeyOrNull.trim()
                : defaultMode.trim();
        String mapped = resolveModeKeyToModelId(modeKey);
        if (!StringUtils.hasText(mapped)) {
            throw new IllegalArgumentException(
                    (StringUtils.hasText(displayName) ? displayName : code)
                            + " 未知 mode: "
                            + modeKey
                            + "，可用: "
                            + modeEntries.keySet());
        }
        return normalizeUpstreamModelId(mapped.trim());
    }

    private static String normalizeUpstreamModelId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw;
        }
        String s = raw.trim();
        String stripped = s.replaceFirst("\\s*\\([^)]*\\)\\s*$", "").trim();
        return stripped.isEmpty() ? s : stripped;
    }

    private String resolveModeKeyToModelId(String modeKey) {
        ModeEntryConfig e = findEntry(modeKey);
        return e != null ? e.getUpstreamModelId() : null;
    }

    private ModeEntryConfig findEntry(String modeKey) {
        if (!StringUtils.hasText(modeKey)) {
            return null;
        }
        if (modeEntries.containsKey(modeKey)) {
            return modeEntries.get(modeKey);
        }
        for (Map.Entry<String, ModeEntryConfig> en : modeEntries.entrySet()) {
            if (en.getKey() != null && en.getKey().equalsIgnoreCase(modeKey.trim())) {
                return en.getValue();
            }
        }
        return null;
    }

    public InferenceDefaults defaultsOrEmpty() {
        return inferenceDefaults != null ? inferenceDefaults : new InferenceDefaults();
    }

    /**
     * 是否按 OpenAI 兼容形态向下游写 {@code thinking}，并参与对话里「深度思考」开关展示。
     * 优先读推理默认 {@code supportsThinkingApi}；为空时按 code/baseUrl 推断（DeepSeek、DashScope/百炼等）。
     */
    public boolean supportsThinkingFlag() {
        Boolean override = defaultsOrEmpty().getSupportsThinkingApi();
        if (override != null) {
            return Boolean.TRUE.equals(override);
        }
        return inferGatewayThinkingSupport(code, baseUrl);
    }

    /**
     * 与 {@link #supportsThinkingFlag()} 在未配置覆盖时的规则一致（供 modes 旧数据解析等复用）。
     */
    public static boolean inferGatewayThinkingSupport(String providerCode, String baseUrl) {
        if (StringUtils.hasText(providerCode)) {
            String c = providerCode.toUpperCase(Locale.ROOT);
            if ("DEEPSEEK".equals(c) || c.contains("DEEPSEEK")) {
                return true;
            }
            if (c.contains("BAILIAN") || c.contains("DASHSCOPE")) {
                return true;
            }
            if (c.contains("CODINGPLAN") || c.contains("CODING_PLAN")) {
                return true;
            }
        }
        if (StringUtils.hasText(baseUrl)) {
            String u = baseUrl.toLowerCase(Locale.ROOT);
            if (u.contains("deepseek")) {
                return true;
            }
            if (u.contains("dashscope") || u.contains("bailian")) {
                return true;
            }
            if (u.contains("aliyuncs.com") && u.contains("compatible-mode")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当前逻辑 mode 是否在配置中开启「深度思考」能力（与网关是否支持 thinking API 无关）。
     */
    public boolean modeSupportsDeepThinking(String modeKey) {
        ModeEntryConfig e = findEntry(modeKey);
        return e != null && e.isDeepThinking();
    }

    private String resolveEffectiveModeKey(ChatRequestDto request) {
        if (request != null && StringUtils.hasText(request.getMode())) {
            return request.getMode().trim();
        }
        return defaultMode != null ? defaultMode.trim() : "";
    }

    /**
     * 是否应展示并持久化 {@code reasoning_content}，且向下游写 thinking：
     * 用户/默认打开思考、网关支持 thinking API、且当前 mode 配置允许深度思考。
     */
    public boolean resolveThinkingContentWanted(ChatRequestDto request) {
        if (request == null) {
            return false;
        }
        Boolean fromReq = request.getThinkingMode();
        boolean toggleOn = fromReq != null
                ? Boolean.TRUE.equals(fromReq)
                : Boolean.TRUE.equals(defaultsOrEmpty().getThinkingMode());
        if (!toggleOn) {
            return false;
        }
        return supportsThinkingFlag() && modeSupportsDeepThinking(resolveEffectiveModeKey(request));
    }

    public Map<String, ModeCapabilityDto> buildModeCapabilitiesDto() {
        LinkedHashMap<String, ModeCapabilityDto> out = new LinkedHashMap<String, ModeCapabilityDto>();
        for (Map.Entry<String, ModeEntryConfig> en : modeEntries.entrySet()) {
            ModeCapabilityDto d = new ModeCapabilityDto();
            d.setTextGeneration(en.getValue().isTextGeneration());
            d.setDeepThinking(en.getValue().isDeepThinking());
            d.setVision(en.getValue().isVision());
            d.setContextWindow(en.getValue().getContextWindow());
            out.put(en.getKey(), d);
        }
        return out;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getDefaultMode() {
        return defaultMode;
    }

    /** 逻辑名 → 上游 model id（兼容旧接口） */
    public Map<String, String> getModes() {
        return modesFlat;
    }
}
