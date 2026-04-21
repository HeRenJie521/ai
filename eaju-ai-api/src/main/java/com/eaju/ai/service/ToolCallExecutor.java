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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 执行单次 AI 工具 HTTP 调用。
 *
 * 请求体构建优先级：
 * 1. dataParamsJson 存在 → 按参数树构建（支持静态值、用户上下文引用、对象参数）
 * 2. bodyTemplate 存在  → {{var}} 模板替换
 * 3. 默认              → 将 LLM 入参序列化为 JSON 或 form-urlencoded
 *
 * contentType = application/json            → 整体包装为 JSON
 * contentType = application/x-www-form-urlencoded → 整理为 key=value&key=value
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

    public String execute(AiToolEntity tool, String toolArgs, Map<String, Object> userCtx) {
        return execute(tool, toolArgs, userCtx, null);
    }

    public String execute(AiToolEntity tool, String toolArgs, Map<String, Object> userCtx,
                          Map<String, String> extendedParams) {
        try {
            Map<String, Object> argsMap = parseArgs(toolArgs);

            log.info("[工具调用] 工具={} ({})", tool.getLabel(), tool.getName());
            log.info("[工具调用] LLM 传入参数：{}", toolArgs);
            if (userCtx == null || userCtx.isEmpty()) {
                log.info("[工具调用] 用户上下文：(空)");
            } else {
                log.info("[工具调用] 用户上下文：{}", userCtx);
            }

            Map<String, Object> vars = new LinkedHashMap<>();
            if (userCtx != null) vars.putAll(userCtx);
            vars.putAll(argsMap);

            String url = substitute(tool.getUrl(), vars);

            HttpHeaders headers = new HttpHeaders();
            if (StringUtils.hasText(tool.getHeadersJson())) {
                Map<String, Object> rawHeaders = objectMapper.readValue(
                        substitute(tool.getHeadersJson(), vars),
                        new TypeReference<Map<String, Object>>() {});
                rawHeaders.forEach((k, v) -> { if (v != null) headers.set(k, v.toString()); });
            }

            String method = tool.getHttpMethod() != null ? tool.getHttpMethod().toUpperCase() : "POST";
            String body = null;

            if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
                String contentType = StringUtils.hasText(tool.getContentType())
                        ? tool.getContentType() : "application/json";
                headers.set("Content-Type", contentType);

                if (StringUtils.hasText(tool.getDataParamsJson())) {
                    body = buildParamBody(tool, argsMap, userCtx, contentType, extendedParams);
                } else if (StringUtils.hasText(tool.getBodyTemplate())) {
                    body = substitute(tool.getBodyTemplate(), vars);
                    if ("application/x-www-form-urlencoded".equals(contentType)) {
                        body = convertJsonToFormUrlEncoded(body);
                    }
                } else {
                    body = "application/x-www-form-urlencoded".equals(contentType)
                            ? convertMapToFormUrlEncoded(argsMap)
                            : objectMapper.writeValueAsString(argsMap);
                }
            }

            log.info("[工具调用] 请求：{} {}", method, url);
            log.info("[工具调用] 请求体：{}", body);

            HttpEntity<String> entity = new HttpEntity<>(body, headers);

            log.info("========== 工具调用请求开始 ==========");
            log.info("[请求信息] toolName={} method={} url={}", tool.getName(), method, url);
            log.info("[请求头] headers={}", headers);
            log.info("[请求体] body={}", body);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.valueOf(method), entity, String.class);
            String result = response.getBody();

            log.info("[响应状态] status={}", response.getStatusCode());
            log.info("[响应体] body={}", result);
            log.info("========== 工具调用请求结束 ==========");

            if (StringUtils.hasText(tool.getResponseParamsJson()) && StringUtils.hasText(result)) {
                String filtered = filterResponseByParams(result, tool.getResponseParamsJson());
                String fieldDesc = buildResponseFieldDesc(tool.getResponseParamsJson());
                StringBuilder sb = new StringBuilder();
                if (StringUtils.hasText(fieldDesc)) {
                    sb.append("字段说明：\n").append(fieldDesc).append("\n");
                }
                // 严格只发过滤后的数据，绝不 fallback 到完整响应（避免泄露未配置字段）
                sb.append("响应数据：\n").append(filtered != null ? filtered : "{}");
                String payload = sb.toString();
                log.info("[出参发给AI] {}", payload);
                return payload;
            }
            return result != null ? result : "";

        } catch (Exception e) {
            log.warn("[工具调用] 失败：tool={} error={}", tool.getName(), e.getMessage());
            return "{\"error\": \"工具调用失败：" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    /**
     * 执行工具调用并返回原始响应结果（用于测试接口）。
     * 如果配置了出参管理，返回过滤后的响应数据；否则返回全部响应数据。
     */
    public String executeWithRawResult(AiToolEntity tool, String toolArgs, Map<String, Object> userCtx) {
        return executeWithRawResult(tool, toolArgs, userCtx, null);
    }

    public String executeWithRawResult(AiToolEntity tool, String toolArgs, Map<String, Object> userCtx,
                                        Map<String, String> extendedParams) {
        try {
            Map<String, Object> argsMap = parseArgs(toolArgs);
            Map<String, Object> vars = new LinkedHashMap<>();
            if (userCtx != null) vars.putAll(userCtx);
            vars.putAll(argsMap);

            String url = substitute(tool.getUrl(), vars);

            HttpHeaders headers = new HttpHeaders();
            if (StringUtils.hasText(tool.getHeadersJson())) {
                Map<String, Object> rawHeaders = objectMapper.readValue(
                        substitute(tool.getHeadersJson(), vars),
                        new TypeReference<Map<String, Object>>() {});
                rawHeaders.forEach((k, v) -> { if (v != null) headers.set(k, v.toString()); });
            }

            String method = tool.getHttpMethod() != null ? tool.getHttpMethod().toUpperCase() : "POST";
            String body = null;

            if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
                String contentType = StringUtils.hasText(tool.getContentType())
                        ? tool.getContentType() : "application/json";
                headers.set("Content-Type", contentType);

                if (StringUtils.hasText(tool.getDataParamsJson())) {
                    body = buildParamBody(tool, argsMap, userCtx, contentType, extendedParams);
                } else if (StringUtils.hasText(tool.getBodyTemplate())) {
                    body = substitute(tool.getBodyTemplate(), vars);
                    if ("application/x-www-form-urlencoded".equals(contentType)) {
                        body = convertJsonToFormUrlEncoded(body);
                    }
                } else {
                    body = "application/x-www-form-urlencoded".equals(contentType)
                            ? convertMapToFormUrlEncoded(argsMap)
                            : objectMapper.writeValueAsString(argsMap);
                }
            }

            HttpEntity<String> entity = new HttpEntity<>(body, headers);

            // 打印请求报文
            log.info("========== 工具调用请求开始 ==========");
            log.info("[请求信息] toolName={} method={} url={}", tool.getName(), method, url);
            log.info("[请求头] headers={}", headers);
            log.info("[请求体] body={}", body);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.valueOf(method), entity, String.class);
            String result = response.getBody();
            
            // 打印响应报文
            log.info("[响应状态] status={}", response.getStatusCode());
            log.info("[响应体] body={}", result);
            log.info("========== 工具调用请求结束 ==========");

            // 如果配置了出参管理，过滤响应数据；否则返回全部
            if (StringUtils.hasText(tool.getResponseParamsJson()) && StringUtils.hasText(result)) {
                String filtered = filterResponseByParams(result, tool.getResponseParamsJson());
                log.info("[出参过滤] 过滤后结果={}", filtered);
                return filtered;
            }
            return result != null ? result : "";

        } catch (Exception e) {
            log.warn("[工具调用] 失败：tool={} error={}", tool.getName(), e.getMessage());
            return "{\"error\": \"工具调用失败：" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    /**
     * 仅构建请求体，不发起 HTTP 调用，供测试接口展示实际入参。
     */
    public String buildRequestBody(AiToolEntity tool, String toolArgs, Map<String, Object> userCtx) {
        return buildRequestBody(tool, toolArgs, userCtx, null);
    }

    public String buildRequestBody(AiToolEntity tool, String toolArgs, Map<String, Object> userCtx,
                                    Map<String, String> extendedParams) {
        try {
            Map<String, Object> argsMap = parseArgs(toolArgs);
            Map<String, Object> vars = new LinkedHashMap<>();
            if (userCtx != null) vars.putAll(userCtx);
            vars.putAll(argsMap);

            String method = tool.getHttpMethod() != null ? tool.getHttpMethod().toUpperCase() : "POST";
            if (!("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))) {
                return null;
            }
            String contentType = StringUtils.hasText(tool.getContentType())
                    ? tool.getContentType() : "application/json";

            if (StringUtils.hasText(tool.getDataParamsJson())) {
                return buildParamBody(tool, argsMap, userCtx, contentType, extendedParams);
            } else if (StringUtils.hasText(tool.getBodyTemplate())) {
                String body = substitute(tool.getBodyTemplate(), vars);
                return "application/x-www-form-urlencoded".equals(contentType)
                        ? convertJsonToFormUrlEncoded(body) : body;
            } else {
                return "application/x-www-form-urlencoded".equals(contentType)
                        ? convertMapToFormUrlEncoded(argsMap)
                        : objectMapper.writeValueAsString(argsMap);
            }
        } catch (Exception e) {
            log.debug("构建测试请求体失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 dataParamsJson 参数树构建请求体。
     * 参数树格式（每项）：
     *   {"key":"methodName","valueType":"static","value":"xxx"}
     *   {"key":"userId","valueType":"context","fieldKey":"esusMobile"}
     *   {"key":"data","valueType":"object","children":[...]}
     * 支持无限层级嵌套，Object/Array 类型的参数可以继续配置子参数。
     */
    private String buildParamBody(AiToolEntity tool, Map<String, Object> argsMap,
                                   Map<String, Object> userCtx, String contentType) throws Exception {
        return buildParamBody(tool, argsMap, userCtx, contentType, null);
    }

    private String buildParamBody(AiToolEntity tool, Map<String, Object> argsMap,
                                   Map<String, Object> userCtx, String contentType,
                                   Map<String, String> extendedParams) throws Exception {
        List<Map<String, Object>> paramDefs = objectMapper.readValue(
                tool.getDataParamsJson(), new TypeReference<List<Map<String, Object>>>() {});

        Map<String, Object> bodyMap = resolveParamTree(paramDefs, argsMap, userCtx, extendedParams);

        if ("application/x-www-form-urlencoded".equals(contentType)) {
            return convertMapToFormUrlEncoded(bodyMap);
        }
        return objectMapper.writeValueAsString(bodyMap);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveParamTree(List<Map<String, Object>> paramDefs,
                                                  Map<String, Object> argsMap,
                                                  Map<String, Object> userCtx,
                                                  Map<String, String> extendedParams) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (paramDefs == null) return result;
        for (Map<String, Object> def : paramDefs) {
            String key = (String) def.get("key");
            if (!StringUtils.hasText(key)) continue;

            String fieldType = (String) def.getOrDefault("fieldType", "String");
            Object childrenObj = def.get("children");
            List<Map<String, Object>> children = (childrenObj instanceof List)
                    ? (List<Map<String, Object>>) childrenObj : null;

            if ("Object".equals(fieldType) && children != null && !children.isEmpty()) {
                Map<String, Object> childResult = resolveParamTree(children, argsMap, userCtx, extendedParams);
                if (!childResult.isEmpty()) result.put(key, childResult);
            } else if ("Array".equals(fieldType) && children != null) {
                result.put(key, resolveArrayChildren(children, argsMap, userCtx, extendedParams));
            } else {
                Object value = resolveLeafValue(def, argsMap, userCtx, extendedParams);
                if (value != null) result.put(key, value);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Object> resolveArrayChildren(List<Map<String, Object>> children,
                                               Map<String, Object> argsMap,
                                               Map<String, Object> userCtx,
                                               Map<String, String> extendedParams) {
        List<Object> list = new ArrayList<>();
        for (Map<String, Object> child : children) {
            String fieldType = (String) child.getOrDefault("fieldType", "String");
            Object childrenObj = child.get("children");
            List<Map<String, Object>> grandchildren = (childrenObj instanceof List)
                    ? (List<Map<String, Object>>) childrenObj : null;

            if ("Object".equals(fieldType) && grandchildren != null && !grandchildren.isEmpty()) {
                list.add(resolveParamTree(grandchildren, argsMap, userCtx, extendedParams));
            } else if ("Array".equals(fieldType) && grandchildren != null) {
                list.add(resolveArrayChildren(grandchildren, argsMap, userCtx, extendedParams));
            } else {
                Object value = resolveLeafValue(child, argsMap, userCtx, extendedParams);
                if (value != null) list.add(value);
            }
        }
        return list;
    }

    private Object resolveLeafValue(Map<String, Object> def,
                                     Map<String, Object> argsMap,
                                     Map<String, Object> userCtx,
                                     Map<String, String> extendedParams) {
        String paramKey = (String) def.get("key");
        String valueSource = (String) def.getOrDefault("valueSource", def.get("valueType"));
        if (valueSource == null) valueSource = "static";

        Object resolved;
        if ("context".equals(valueSource)) {
            String fieldKey = (String) def.get("fieldKey");
            resolved = (userCtx != null && StringUtils.hasText(fieldKey)) ? userCtx.get(fieldKey) : null;
            log.info("[参数解析] key={} 来源=用户上下文 fieldKey={} 取到值={}", paramKey, fieldKey, resolved);
        } else if ("apikey".equals(valueSource)) {
            String fieldKey = (String) def.get("fieldKey");
            resolved = (extendedParams != null && StringUtils.hasText(fieldKey))
                    ? extendedParams.get(fieldKey) : null;
            log.info("[参数解析] key={} 来源=APIKey扩展参数 fieldKey={} 取到值={}", paramKey, fieldKey, resolved);
        } else if ("llm".equals(valueSource)) {
            resolved = StringUtils.hasText(paramKey) ? argsMap.get(paramKey) : null;
            log.info("[参数解析] key={} 来源=LLM 入参 取到值={}", paramKey, resolved);
        } else if ("response".equals(valueSource)) {
            resolved = null;
            log.info("[参数解析] key={} 来源=出参传递 跳过", paramKey);
        } else {
            String raw = (String) def.get("sourceValue");
            if (raw == null) raw = (String) def.get("value");
            resolved = raw != null ? substitute(raw, argsMap) : null;
            log.info("[参数解析] key={} 来源=静态值 原始值={} 替换后={}", paramKey, raw, resolved);
        }
        return resolved;
    }

    private Map<String, Object> parseArgs(String toolArgs) {
        if (!StringUtils.hasText(toolArgs)) return new LinkedHashMap<>();
        try {
            Map<String, Object> map = objectMapper.readValue(
                    toolArgs.trim(), new TypeReference<Map<String, Object>>() {});
            return map != null ? map : new LinkedHashMap<>();
        } catch (Exception e) {
            log.warn("解析工具入参失败：{}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    static String substitute(String template, Map<String, Object> vars) {
        if (!StringUtils.hasText(template) || vars == null || vars.isEmpty()) return template;
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String varName = m.group(1);
            Object val = vars.get(varName);
            m.appendReplacement(sb, val != null ? Matcher.quoteReplacement(val.toString()) : m.group(0));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 将 Map 转为 form-urlencoded 字符串。
     * 值若为 Map/List（对象参数子级），JSON 序列化后 URL 编码。
     */
    private String convertMapToFormUrlEncoded(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append("&");
            try {
                sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name()));
                sb.append("=");
                if (entry.getValue() != null) {
                    String strVal;
                    if (entry.getValue() instanceof Map || entry.getValue() instanceof List) {
                        strVal = objectMapper.writeValueAsString(entry.getValue());
                    } else {
                        strVal = entry.getValue().toString();
                    }
                    sb.append(URLEncoder.encode(strVal, StandardCharsets.UTF_8.name()));
                }
            } catch (Exception ignored) {}
            first = false;
        }
        return sb.toString();
    }

    private String convertJsonToFormUrlEncoded(String jsonBody) {
        if (!StringUtils.hasText(jsonBody)) return "";
        try {
            Map<String, Object> map = objectMapper.readValue(
                    jsonBody, new TypeReference<Map<String, Object>>() {});
            if (map != null && !map.isEmpty()) return convertMapToFormUrlEncoded(map);
        } catch (Exception e) {
            log.debug("JSON 解析失败，可能已是 form-urlencoded 格式：{}", e.getMessage());
        }
        return jsonBody;
    }

    /**
     * 从 responseParamsJson 生成字段说明文本，供 LLM 理解返回值含义。
     * 支持无限层级嵌套。
     * 格式示例：
     *   - code (String): 响应码
     *   - data (Object): 数据体
     *     - data.ebcdCode (String): 数据字典编码 | 可用于数据提交
     *     - data.items (Array): 明细列表
     *       - data.items[*].itemId (String): 明细 ID
     */
    @SuppressWarnings("unchecked")
    private String buildResponseFieldDesc(String responseParamsJson) {
        try {
            List<Map<String, Object>> params = objectMapper.readValue(
                    responseParamsJson, new TypeReference<List<Map<String, Object>>>() {});
            StringBuilder sb = new StringBuilder();
            appendFieldDesc(params, "", sb, 0);
            return sb.toString().trim();
        } catch (Exception e) {
            log.debug("解析出参说明 JSON 失败：{}", e.getMessage());
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private void appendFieldDesc(List<Map<String, Object>> params, String prefix,
                                  StringBuilder sb, int depth) {
        StringBuilder indentSb = new StringBuilder();
        for (int d = 0; d < depth; d++) indentSb.append("  ");
        String indent = indentSb.toString();
        for (Map<String, Object> param : params) {
            String key = (String) param.get("key");
            if (!StringUtils.hasText(key)) continue;
            String fieldType  = (String) param.getOrDefault("fieldType", "String");
            String label      = (String) param.getOrDefault("label", "");
            String desc       = (String) param.getOrDefault("description", "");
            String fullKey    = prefix.isEmpty() ? key : prefix + "." + key;

            sb.append(indent).append("- ").append(fullKey)
              .append(" (").append(fieldType).append("): ").append(label);
            if (StringUtils.hasText(desc)) {
                sb.append(" | ").append(desc);
            }
            sb.append("\n");

            List<Map<String, Object>> children = (List<Map<String, Object>>) param.get("children");
            if (children != null && !children.isEmpty()) {
                // Array 类型子路径加 [*] 标识
                String childPrefix = "Array".equalsIgnoreCase(fieldType)
                        ? fullKey + "[*]" : fullKey;
                appendFieldDesc(children, childPrefix, sb, depth + 1);
            }
        }
    }

    /**
     * 根据配置的出参过滤响应结果，只保留配置的字段。
     * 如果响应是 JSON 且能解析，则按配置的出参树进行过滤；
     * 如果响应不是 JSON 或解析失败，则原样返回。
     */
    @SuppressWarnings("unchecked")
    private String filterResponseByParams(String rawResult, String responseParamsJson) {
        if (!StringUtils.hasText(rawResult)) {
            return rawResult;
        }
        try {
            // 尝试解析响应为 JSON
            Object responseObj;
            try {
                responseObj = objectMapper.readValue(rawResult.trim(), Object.class);
            } catch (Exception e) {
                // 不是 JSON，原样返回
                return rawResult;
            }

            // 解析出参配置
            List<Map<String, Object>> params = objectMapper.readValue(
                    responseParamsJson, new TypeReference<List<Map<String, Object>>>() {});

            // 构建过滤后的结果
            Map<String, Object> filtered = new LinkedHashMap<>();
            for (Map<String, Object> param : params) {
                String key = (String) param.get("key");
                if (!StringUtils.hasText(key)) continue;

                String fieldType = (String) param.getOrDefault("fieldType", "String");
                List<Map<String, Object>> children = (List<Map<String, Object>>) param.get("children");

                if ("Object".equals(fieldType) && children != null && !children.isEmpty()) {
                    // 对象类型：递归提取已配置的子字段
                    if (responseObj instanceof Map) {
                        Object childValue = ((Map<?, ?>) responseObj).get(key);
                        Object filteredChild = filterByConfig(childValue, children);
                        if (filteredChild != null) {
                            filtered.put(key, filteredChild);
                        }
                    }
                } else if ("Object".equals(fieldType)) {
                    // Object 类型但没有配置子字段 → 跳过，不整体透传
                    log.warn("[出参过滤] 字段 {} 类型为 Object 但未配置子字段，已跳过", key);
                } else if ("Array".equals(fieldType) && children != null && !children.isEmpty()) {
                    // 数组类型：递归过滤每个元素的子字段
                    if (responseObj instanceof Map) {
                        Object arrayValue = ((Map<?, ?>) responseObj).get(key);
                        if (arrayValue instanceof List) {
                            List<Object> filteredArray = new ArrayList<>();
                            for (Object item : (List<?>) arrayValue) {
                                Object filteredItem = filterByConfig(item, children);
                                if (filteredItem != null) {
                                    filteredArray.add(filteredItem);
                                }
                            }
                            filtered.put(key, filteredArray);
                        }
                    }
                } else if ("Array".equals(fieldType)) {
                    // Array 类型但没有配置子字段 → 跳过
                    log.warn("[出参过滤] 字段 {} 类型为 Array 但未配置子字段，已跳过", key);
                } else {
                    // 叶子节点：直接取值
                    if (responseObj instanceof Map) {
                        Object value = ((Map<?, ?>) responseObj).get(key);
                        if (value != null) {
                            filtered.put(key, value);
                        }
                    }
                }
            }

            // 如果过滤后有数据，返回 JSON；否则返回空字符串
            if (filtered.isEmpty()) {
                return "";
            }
            return objectMapper.writeValueAsString(filtered);

        } catch (Exception e) {
            log.debug("过滤出参失败：{}", e.getMessage());
            // 过滤失败时原样返回
            return rawResult;
        }
    }

    /**
     * 递归过滤单个值（对象或数组元素），根据配置的子参数树提取字段。
     */
    @SuppressWarnings("unchecked")
    private Object filterByConfig(Object value, List<Map<String, Object>> children) {
        if (value == null || children == null || children.isEmpty()) {
            return value;
        }

        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Map<String, Object> result = new LinkedHashMap<>();

            for (Map<String, Object> child : children) {
                String key = (String) child.get("key");
                if (!StringUtils.hasText(key)) continue;

                String fieldType = (String) child.getOrDefault("fieldType", "String");
                List<Map<String, Object>> grandchildren = (List<Map<String, Object>>) child.get("children");

                if ("Object".equals(fieldType) && grandchildren != null && !grandchildren.isEmpty()) {
                    Object childValue = map.get(key);
                    Object filteredChild = filterByConfig(childValue, grandchildren);
                    if (filteredChild != null) {
                        result.put(key, filteredChild);
                    }
                } else if ("Object".equals(fieldType)) {
                    // Object 类型但无子字段配置 → 跳过，不整体透传
                } else if ("Array".equals(fieldType) && grandchildren != null && !grandchildren.isEmpty()) {
                    Object arrayValue = map.get(key);
                    log.info("[filterByConfig] Array key={} found={} isList={}", key, arrayValue != null, arrayValue instanceof List);
                    if (arrayValue instanceof List) {
                        List<Object> filteredArray = new ArrayList<>();
                        for (Object item : (List<?>) arrayValue) {
                            log.info("[filterByConfig] Array item type={} value={}", item == null ? "null" : item.getClass().getSimpleName(), item);
                            Object filteredItem = filterByConfig(item, grandchildren);
                            log.info("[filterByConfig] Array item filtered={}", filteredItem);
                            if (filteredItem != null) {
                                filteredArray.add(filteredItem);
                            }
                        }
                        result.put(key, filteredArray);
                    }
                } else if ("Array".equals(fieldType)) {
                    // Array 类型但无子字段配置 → 跳过
                } else {
                    // 叶子节点
                    Object childValue = map.get(key);
                    log.info("[filterByConfig] Leaf key={} mapKeys={} found={}", key, map.keySet(), childValue);
                    if (childValue != null) {
                        result.put(key, childValue);
                    }
                }
            }
            return result.isEmpty() ? null : result;
        }

        return value;
    }
}
