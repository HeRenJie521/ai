package com.eaju.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户上下文缓存：在应用嵌入登录时将额外上下文字段（如部门、工号）存入 Redis，
 * 工具调用时按 jti 读取，用于 {{var}} 模板变量替换。
 * Key 格式：ctx:{jti}
 */
@Service
public class UserContextCacheService {

    private static final Logger log = LoggerFactory.getLogger(UserContextCacheService.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final long ttlHours;

    public UserContextCacheService(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.jwt.expiration-hours:168}") long jwtExpirationHours) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.ttlHours = jwtExpirationHours > 0 ? jwtExpirationHours : 168L;
    }

    private String key(String jti) {
        return "ctx:" + jti;
    }

    public void save(String jti, Map<String, Object> context) {
        if (!StringUtils.hasText(jti) || context == null || context.isEmpty()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(context);
            stringRedisTemplate.opsForValue().set(key(jti), json, ttlHours, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("写入用户上下文缓存失败: {}", e.getMessage());
        }
    }

    public Map<String, Object> get(String jti) {
        if (!StringUtils.hasText(jti)) {
            return Collections.emptyMap();
        }
        String json = stringRedisTemplate.opsForValue().get(key(jti));
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> ctx = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            return ctx != null ? ctx : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("读取用户上下文缓存失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    public void delete(String jti) {
        if (StringUtils.hasText(jti)) {
            stringRedisTemplate.delete(key(jti));
        }
    }
}
