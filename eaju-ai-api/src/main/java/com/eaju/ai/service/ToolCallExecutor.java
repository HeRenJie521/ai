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
                if (StringUtils.hasText(tool.getBodyTemplate())) {
                    body = substitute(tool.getBodyTemplate(), vars);
                } else {
                    // 默认将 argsMap 作为 JSON body
                    body = objectMapper.writeValueAsString(argsMap);
                    headers.set("Content-Type", "application/json");
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
}
