package com.eaju.ai.service;

import com.eaju.ai.dto.auth.LoginSessionSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 登录会话缓存：与 JWT {@code jti} 一致，后续请求需 Redis 中存在会话才视为有效（与无状态 JWT 叠加）。
 */
@Service
public class LoginSessionCacheService {

    private static final Logger log = LoggerFactory.getLogger(LoginSessionCacheService.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;
    private final long ttlHours;

    public LoginSessionCacheService(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.auth.session-redis-key-prefix:auth:session:}") String keyPrefix,
            @Value("${app.auth.session-ttl-hours:0}") long sessionTtlHours,
            @Value("${app.jwt.expiration-hours:168}") long jwtExpirationHours) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix;
        long ttl = sessionTtlHours > 0 ? sessionTtlHours : jwtExpirationHours;
        this.ttlHours = ttl > 0 ? ttl : 168L;
    }

    private String key(String jti) {
        return keyPrefix + jti;
    }

    public void save(String jti, LoginSessionSnapshot snapshot) {
        if (!StringUtils.hasText(jti) || snapshot == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            stringRedisTemplate.opsForValue().set(key(jti), json, ttlHours, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.warn("序列化登录会话失败: {}", e.getMessage());
            throw new IllegalStateException("登录会话写入缓存失败");
        }
    }

    public boolean exists(String jti) {
        if (!StringUtils.hasText(jti)) {
            return false;
        }
        Boolean b = stringRedisTemplate.hasKey(key(jti));
        return Boolean.TRUE.equals(b);
    }

    public Optional<LoginSessionSnapshot> get(String jti) {
        if (!StringUtils.hasText(jti)) {
            return Optional.empty();
        }
        String json = stringRedisTemplate.opsForValue().get(key(jti));
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, LoginSessionSnapshot.class));
        } catch (Exception e) {
            log.warn("反序列化登录会话失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void delete(String jti) {
        if (!StringUtils.hasText(jti)) {
            return;
        }
        stringRedisTemplate.delete(key(jti));
    }
}
