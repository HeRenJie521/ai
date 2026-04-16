package com.eaju.ai.service;

import com.eaju.ai.persistence.entity.AiToolEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 执行单次 AI 工具 HTTP 调用，支持 {{var}} 模板变量替换。
 * 变量来源：工具调用入参（LLM 生成）+ 用户上下文（Redis 存储）。
 */
@Component
public class ToolCallExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolCallExecutor.class);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ToolCallExecutor(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行工具调用并返回结果字符串（供 LLM 作为 tool 消息内容）。
     *
     * @param tool       工具定义
     * @param toolArgs   LLM 生成的工具入参 JSON 字符串
     * @param userCtx    用户上下文 Map（来自 Redis）
     * @return 工具执行结果字符串
     */
    public String execute(AiToolEntity tool, String toolArgs, Map<String, Object> userCtx) {
        try {
            // 解析 LLM 生成的入参
            Map<String, Object> argsMap = parseArgs(toolArgs);

            // 合并上下文：用户上下文优先级低于工具入参
            Map<String, Object> vars = new java.util.HashMap<>();
            if (userCtx != null) vars.putAll(userCtx);
            vars.putAll(argsMap);

            // 构建请求 URL
            String url = substitute(tool.getUrl(), vars);

            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            if (StringUtils.hasText(tool.getHeadersJson())) {
                Map<String, Object> rawHeaders = objectMapper.readValue(
                        substitute(tool.getHeadersJson(), vars),
                        new TypeReference<Map<String, Object>>() {});
                rawHeaders.forEach((k, v) -> {
                    if (v != null) headers.set(k, v.toString());
                });
            }

            // 构建请求体（POST/PUT 时使用）
            String body = null;
            String method = tool.getHttpMethod() != null ? tool.getHttpMethod().toUpperCase() : "POST";
            if (("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))) {
                // 设置 Content-Type
                String contentType = tool.getContentType() != null ? tool.getContentType() : "application/json";
                headers.set("Content-Type", contentType);
                
                if (StringUtils.hasText(tool.getBodyTemplate())) {
                    body = substitute(tool.getBodyTemplate(), vars);
                    // 如果是 form-urlencoded 且使用模板，需要手动转换
                    if ("application/x-www-form-urlencoded".equals(contentType)) {
                        body = convertJsonToFormUrlEncoded(body);
                    }
                } else {
                    // 默认将 argsMap 作为 body
                    if ("application/x-www-form-urlencoded".equals(contentType)) {
                        // 将 argsMap 转换为 form-urlencoded 格式
                        body = convertMapToFormUrlEncoded(argsMap);
                    } else {
                        // JSON 格式
                        body = objectMapper.writeValueAsString(argsMap);
                    }
                }
            }

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            HttpMethod httpMethod = HttpMethod.valueOf(method);

            log.debug("工具调用: {} {} body={}", method, url, body);
            ResponseEntity<String> response = restTemplate.exchange(url, httpMethod, entity, String.class);

            String result = response.getBody();
            log.debug("工具响应: status={} body={}", response.getStatusCode(), result);
            return result != null ? result : "";
        } catch (Exception e) {
            log.warn("工具调用失败: tool={} error={}", tool.getName(), e.getMessage());
            return "{\"error\": \"工具调用失败: " + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    private Map<String, Object> parseArgs(String toolArgs) {
        if (!StringUtils.hasText(toolArgs)) {
            return new java.util.HashMap<>();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(toolArgs.trim(),
                    new TypeReference<Map<String, Object>>() {});
            return map != null ? map : new java.util.HashMap<>();
        } catch (Exception e) {
            log.warn("解析工具入参失败: {}", e.getMessage());
            return new java.util.HashMap<>();
        }
    }

    /**
     * 将字符串中的 {{varName}} 替换为 vars 中对应值（toString），未找到则保留原占位符。
     */
    static String substitute(String template, Map<String, Object> vars) {
        if (!StringUtils.hasText(template) || vars == null || vars.isEmpty()) {
            return template;
        }
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String varName = m.group(1);
            Object val = vars.get(varName);
            String replacement = val != null ? Matcher.quoteReplacement(val.toString()) : m.group(0);
            m.appendReplacement(sb, replacement);
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 将 Map 转换为 form-urlencoded 格式
     * 例如：{"key": "value"} -> "key=value"
     */
    private String convertMapToFormUrlEncoded(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append("&");
            }
            try {
                sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name()));
                sb.append("=");
                if (entry.getValue() != null) {
                    sb.append(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8.name()));
                }
            } catch (UnsupportedEncodingException e) {
                // 不会发生，UTF-8 总是受支持
            }
            first = false;
        }
        return sb.toString();
    }

    /**
     * 尝试将 JSON 字符串转换为 form-urlencoded 格式
     * 如果输入已经是 form-urlencoded 格式，则直接返回
     */
    private String convertJsonToFormUrlEncoded(String jsonBody) {
        if (jsonBody == null || jsonBody.isEmpty()) {
            return "";
        }
        // 尝试解析为 JSON
        try {
            Map<String, Object> map = objectMapper.readValue(jsonBody,
                    new TypeReference<Map<String, Object>>() {});
            if (map != null && !map.isEmpty()) {
                return convertMapToFormUrlEncoded(map);
            }
        } catch (Exception e) {
            // 如果不是有效 JSON，可能是已经是 form-urlencoded 格式或模板，直接返回
            log.debug("JSON 解析失败，可能已是 form-urlencoded 格式：{}", e.getMessage());
        }
        return jsonBody;
    }
}
