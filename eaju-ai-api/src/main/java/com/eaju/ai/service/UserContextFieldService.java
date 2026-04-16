package com.eaju.ai.service;

import com.eaju.ai.persistence.entity.UserContextFieldEntity;
import com.eaju.ai.persistence.repository.UserContextFieldRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserContextFieldService {

    private static final Logger log = LoggerFactory.getLogger(UserContextFieldService.class);

    private final UserContextFieldRepository repository;
    private final ObjectMapper objectMapper;

    public UserContextFieldService(UserContextFieldRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<UserContextFieldEntity> listAll() {
        return repository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public List<UserContextFieldEntity> listEnabled() {
        return repository.findByEnabledIsTrueOrderByIdAsc();
    }

    @Transactional
    public UserContextFieldEntity create(String fieldKey, String label, String fieldType,
                                         String parseExpression, String description) {
        if (!StringUtils.hasText(fieldKey)) throw new IllegalArgumentException("fieldKey 不能为空");
        if (!StringUtils.hasText(label)) throw new IllegalArgumentException("显示名不能为空");
        if (repository.findByFieldKey(fieldKey.trim()).isPresent()) {
            throw new IllegalArgumentException("字段 key 已存在: " + fieldKey.trim());
        }
        UserContextFieldEntity e = new UserContextFieldEntity();
        e.setFieldKey(fieldKey.trim());
        e.setLabel(label.trim());
        e.setFieldType(StringUtils.hasText(fieldType) ? fieldType.trim() : "String");
        e.setParseExpression(StringUtils.hasText(parseExpression) ? parseExpression.trim() : "");
        e.setDescription(StringUtils.hasText(description) ? description.trim() : null);
        return repository.save(e);
    }

    @Transactional
    public UserContextFieldEntity update(Long id, String fieldKey, String label, String fieldType,
                                          String parseExpression, String description, Boolean enabled) {
        UserContextFieldEntity e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户数据字段不存在: " + id));
        if (StringUtils.hasText(fieldKey)) {
            String newKey = fieldKey.trim();
            if (!newKey.equals(e.getFieldKey())) {
                if (repository.findByFieldKey(newKey).isPresent()) {
                    throw new IllegalArgumentException("字段 key 已存在: " + newKey);
                }
                e.setFieldKey(newKey);
            }
        }
        if (StringUtils.hasText(label)) e.setLabel(label.trim());
        if (fieldType != null) e.setFieldType(StringUtils.hasText(fieldType) ? fieldType.trim() : "String");
        if (parseExpression != null) e.setParseExpression(parseExpression.trim());
        if (description != null) e.setDescription(StringUtils.hasText(description) ? description.trim() : null);
        if (enabled != null) e.setEnabled(enabled);
        return repository.save(e);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("用户数据字段不存在: " + id);
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public UserContextFieldEntity findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户数据字段不存在: " + id));
    }

    /**
     * 测试单个字段的解析是否能从指定 DMS JSON 中提取到值。
     *
     * @return 提取到的值（String / Long / Double / Boolean / JSON 字符串），未找到时返回 null
     */
    public Object testExtract(String parseExpression, String fieldType, String dmsJson) {
        if (!StringUtils.hasText(dmsJson)) return null;
        try {
            JsonNode root = objectMapper.readTree(dmsJson);
            return resolveExpression(root, parseExpression, fieldType);
        } catch (Exception ex) {
            log.warn("testExtract 解析失败: expr={} error={}", parseExpression, ex.getMessage());
            return null;
        }
    }

    /**
     * 从 DMS 登录 JSON 字符串中，按已启用字段的 parseExpression（dot-notation）提取值，
     * 返回 fieldKey → value 的 Map，供存入 Redis ctx:{jti}。
     *
     * @param dmsJson DMS 登录响应 JSON 字符串（存于 LoginSessionSnapshot.dmsResponseExcerpt）
     */
    public Map<String, Object> extractContext(String dmsJson) {
        Map<String, Object> result = new HashMap<>();
        if (!StringUtils.hasText(dmsJson)) {
            return result;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(dmsJson);
        } catch (Exception ex) {
            log.warn("解析 DMS 登录 JSON 失败: {}", ex.getMessage());
            return result;
        }

        List<UserContextFieldEntity> fields = listEnabled();
        for (UserContextFieldEntity field : fields) {
            String expr = field.getParseExpression();
            if (!StringUtils.hasText(expr)) continue;
            try {
                Object value = resolveExpression(root, expr, field.getFieldType());
                if (value != null) {
                    result.put(field.getFieldKey(), value);
                }
            } catch (Exception ex) {
                log.warn("提取字段 {} (expr={}) 失败: {}", field.getFieldKey(), expr, ex.getMessage());
            }
        }
        return result;
    }

    /**
     * 按 dot-notation 路径从 JsonNode 中提取值。
     * 路径如 "data.esusMobile" 或 "appPermission.sitePermissions"。
     */
    private Object resolveExpression(JsonNode root, String expression, String fieldType) {
        String[] parts = expression.split("\\.");
        JsonNode current = root;
        for (String part : parts) {
            if (current == null || current.isNull() || current.isMissingNode()) {
                return null;
            }
            current = current.path(part);
        }
        if (current == null || current.isNull() || current.isMissingNode()) {
            return null;
        }
        if (current.isTextual()) {
            return current.asText();
        }
        if (current.isNumber()) {
            return current.isIntegralNumber() ? current.longValue() : current.doubleValue();
        }
        if (current.isBoolean()) {
            return current.asBoolean();
        }
        // Object or Array — return as JSON string
        return current.toString();
    }
}
