package com.eaju.ai.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * 回复格式，对应 OpenAI {@code response_format.type}。
 */
public enum ResponseFormatKind {

    /** 普通文本（默认不下发 response_format） */
    TEXT,
    /** JSON 模式，下发 {@code {"type":"json_object"}} */
    JSON_OBJECT;

    @JsonCreator
    public static ResponseFormatKind fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String v = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("JSONOBJECT".equals(v) || "JSON_OBJECT".equals(v)) {
            return JSON_OBJECT;
        }
        return ResponseFormatKind.valueOf(v);
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
