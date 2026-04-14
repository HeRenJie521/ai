package com.eaju.ai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 解析 {@code modes_json}：兼容旧版 {@code { "逻辑名": "上游model" }} 与新版对象值（含能力字段）。
 */
public final class ModesJsonParser {

    private ModesJsonParser() {
    }

    public static Map<String, ModeEntryConfig> parse(String modesJsonRaw, boolean legacyDeepThinkingDefault)
            throws JsonProcessingException {
        ObjectMapper om = new ObjectMapper();
        JsonNode root = om.readTree(modesJsonRaw);
        if (!root.isObject()) {
            throw new BadModesJson("modesJson 必须是 JSON 对象");
        }
        LinkedHashMap<String, ModeEntryConfig> out = new LinkedHashMap<String, ModeEntryConfig>();
        Iterator<Map.Entry<String, JsonNode>> it = root.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> en = it.next();
            String key = en.getKey() != null ? en.getKey().trim() : "";
            if (!StringUtils.hasText(key)) {
                continue;
            }
            JsonNode v = en.getValue();
            if (v == null || v.isNull()) {
                continue;
            }
            if (v.isTextual()) {
                out.put(key, ModeEntryConfig.fromLegacyStringValue(key, v.asText(), legacyDeepThinkingDefault));
            } else if (v.isObject()) {
                out.put(key, parseObjectEntry(key, v));
            } else {
                throw new BadModesJson("modesJson 中键「" + key + "」的值须为字符串或对象");
            }
        }
        return out;
    }

    private static final class BadModesJson extends JsonProcessingException {
        private static final long serialVersionUID = 1L;

        BadModesJson(String msg) {
            super(msg);
        }
    }

    private static ModeEntryConfig parseObjectEntry(String logicalKey, JsonNode o) {
        String upstream = firstNonBlank(
                text(o, "upstreamModel"),
                text(o, "model"),
                text(o, "modelId"),
                logicalKey);
        boolean textGen = o.has("textGeneration") ? o.get("textGeneration").asBoolean(true) : true;
        boolean deep = o.has("deepThinking") ? o.get("deepThinking").asBoolean(false) : false;
        boolean vision = o.has("vision") ? o.get("vision").asBoolean(false) : false;
        Integer contextWindow = null;
        if (o.has("contextWindow") && o.get("contextWindow").isNumber()) {
            contextWindow = o.get("contextWindow").asInt();
        }
        return new ModeEntryConfig(upstream, textGen, deep, vision, contextWindow);
    }

    private static String text(JsonNode o, String field) {
        JsonNode n = o.get(field);
        if (n != null && n.isTextual()) {
            String t = n.asText().trim();
            return StringUtils.hasText(t) ? t : null;
        }
        return null;
    }

    @SafeVarargs
    private static String firstNonBlank(String... parts) {
        if (parts == null) {
            return "";
        }
        for (String p : parts) {
            if (StringUtils.hasText(p)) {
                return p.trim();
            }
        }
        return "";
    }

}
