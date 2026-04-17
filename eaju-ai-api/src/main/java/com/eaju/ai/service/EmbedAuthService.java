package com.eaju.ai.service;

import com.eaju.ai.dto.auth.LoginResponseDto;
import com.eaju.ai.dto.auth.LoginSessionSnapshot;
import com.eaju.ai.dto.embed.AppEmbedLoginRequestDto;
import com.eaju.ai.dto.embed.EmbedLoginRequestDto;
import com.eaju.ai.persistence.entity.AiAppEntity;
import com.eaju.ai.persistence.entity.ApiKeyEntity;
import com.eaju.ai.persistence.entity.UserContextFieldEntity;
import com.eaju.ai.persistence.repository.AiAppRepository;
import com.eaju.ai.persistence.repository.ApiKeyRepository;
import com.eaju.ai.persistence.repository.UserContextFieldRepository;
import com.eaju.ai.security.JwtIssueResult;
import com.eaju.ai.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 嵌入网站（WEB_EMBED）免密单点登录服务。
 * <p>
 * 验证逻辑：对传入的 token 做 SHA-256，与集成存储的 secretHash 比对，一致即放行。
 * 默认模型从集成关联的 AI 应用（ai_app.model_id）读取。
 */
@Service
public class EmbedAuthService {

    private static final Logger log = LoggerFactory.getLogger(EmbedAuthService.class);
    private static final int TYPE_WEB_EMBED = 2;

    private final ApiKeyRepository apiKeyRepository;
    private final AiAppRepository aiAppRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginSessionCacheService loginSessionCacheService;
    private final UserContextFieldRepository userContextFieldRepository;
    private final UserContextCacheService userContextCacheService;

    public EmbedAuthService(ApiKeyRepository apiKeyRepository,
                            AiAppRepository aiAppRepository,
                            JwtTokenProvider jwtTokenProvider,
                            LoginSessionCacheService loginSessionCacheService,
                            UserContextFieldRepository userContextFieldRepository,
                            UserContextCacheService userContextCacheService) {
        this.apiKeyRepository = apiKeyRepository;
        this.aiAppRepository = aiAppRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginSessionCacheService = loginSessionCacheService;
        this.userContextFieldRepository = userContextFieldRepository;
        this.userContextCacheService = userContextCacheService;
    }

    public LoginResponseDto embedLogin(EmbedLoginRequestDto req) {
        String userId = req.getUserId() != null ? req.getUserId().trim() : "";
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        ApiKeyEntity integration = apiKeyRepository
                .findByIdAndTypeAndEnabledIsTrueAndDeletedIsFalse(req.getIntegrationId(), TYPE_WEB_EMBED)
                .orElseThrow(() -> new IllegalArgumentException("集成不存在或已禁用"));

        String storedHash = integration.getSecretHash();
        if (!StringUtils.hasText(storedHash)) {
            throw new IllegalArgumentException("集成配置异常：嵌入凭证未初始化");
        }

        String providedHash = sha256Hex(req.getToken().trim());
        if (!storedHash.equals(providedHash)) {
            log.warn("EmbedLogin 凭证不匹配: integrationId={}, userId={}", req.getIntegrationId(), userId);
            throw new IllegalArgumentException("嵌入凭证不正确");
        }

        String displayName = StringUtils.hasText(req.getUsername()) ? req.getUsername().trim() : userId;
        JwtIssueResult issued = jwtTokenProvider.createEmbedToken(userId, displayName, integration.getId());

        LoginSessionSnapshot snap = new LoginSessionSnapshot();
        snap.setPhone(userId);
        snap.setUsername(displayName);
        snap.setAdmin(false);
        snap.setIssuedAtEpochMs(System.currentTimeMillis());
        snap.setDmsResponseExcerpt("{\"embed\":true,\"integrationId\":" + integration.getId() + "}");
        loginSessionCacheService.save(issued.getJti(), snap);

        log.info("EmbedLogin 成功: integrationId={}, userId={}", integration.getId(), userId);

        LoginResponseDto dto = new LoginResponseDto();
        dto.setToken(issued.getToken());
        dto.setJti(issued.getJti());
        dto.setExpiresIn(jwtTokenProvider.getExpirationSeconds());
        dto.setUserId(userId);
        dto.setPhone(userId);
        dto.setUsername(displayName);
        dto.setAdmin(false);
        dto.setDefaultModel(null);
        dto.setIntegrationName(integration.getName());
        return dto;
    }

    /**
     * 应用管理嵌入登录：无需集成凭证，直接通过 appId 登录。
     * JWT 中携带 appId claim，ChatService 据此直接加载 AI 应用配置。
     */
    public LoginResponseDto appEmbedLogin(AppEmbedLoginRequestDto req) {
        String userId = req.getUserId() != null ? req.getUserId().trim() : "";
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        AiAppEntity app = aiAppRepository.findByIdAndDeletedIsFalse(req.getAppId())
                .orElseThrow(() -> new IllegalArgumentException("AI应用不存在或已删除"));

        String displayName = StringUtils.hasText(req.getUsername()) ? req.getUsername().trim() : userId;
        JwtIssueResult issued = jwtTokenProvider.createAppEmbedToken(userId, displayName, app.getId());

        LoginSessionSnapshot snap = new LoginSessionSnapshot();
        snap.setPhone(userId);
        snap.setUsername(displayName);
        snap.setAdmin(false);
        snap.setIssuedAtEpochMs(System.currentTimeMillis());
        snap.setDmsResponseExcerpt("{\"embed\":true,\"appId\":" + app.getId() + "}");
        loginSessionCacheService.save(issued.getJti(), snap);

        // 将登录数据（userId/username）与 extraContext 合并后存入用户上下文缓存
        Map<String, Object> loginData = new HashMap<>();
        loginData.put("userId", userId);
        loginData.put("username", displayName);
        if (req.getExtraContext() != null) {
            loginData.putAll(req.getExtraContext());
        }
        storeUserContext(issued.getJti(), loginData);

        log.info("AppEmbedLogin 成功: appId={}, userId={}", app.getId(), userId);

        LoginResponseDto dto = new LoginResponseDto();
        dto.setToken(issued.getToken());
        dto.setJti(issued.getJti());
        dto.setExpiresIn(jwtTokenProvider.getExpirationSeconds());
        dto.setUserId(userId);
        dto.setPhone(userId);
        dto.setUsername(displayName);
        dto.setAdmin(false);
        dto.setDefaultModel(app.getModelId());
        dto.setIntegrationName(app.getName());
        return dto;
    }

    /**
     * 将登录数据存入 Redis 用户上下文缓存。
     * <p>
     * 存储规则：
     * 1. userId / username 无条件存入（工具参数可直接用 fieldKey: "userId"、"username"）
     * 2. 遍历 user_context_field 白名单：
     *    - parseExpression 有值 → 按 dot-notation 路径从 loginData 取值（如 "userId"、"data.mobile"）
     *    - parseExpression 为空 → 直接用 fieldKey 从 loginData 取值
     */
    private void storeUserContext(String jti, Map<String, Object> loginData) {
        if (!StringUtils.hasText(jti) || loginData == null || loginData.isEmpty()) {
            return;
        }
        Map<String, Object> ctx = new HashMap<>();

        // 无条件存入基础登录字段
        if (loginData.get("userId") != null)   ctx.put("userId",   loginData.get("userId"));
        if (loginData.get("username") != null)  ctx.put("username", loginData.get("username"));

        // 按白名单字段配置解析额外字段
        List<UserContextFieldEntity> allowedFields = userContextFieldRepository.findByEnabledIsTrueOrderByIdAsc();
        for (UserContextFieldEntity field : allowedFields) {
            String expr = StringUtils.hasText(field.getParseExpression())
                    ? field.getParseExpression() : field.getFieldKey();
            Object value = navigateDotPath(loginData, expr);
            // parseExpression 路径未命中时，直接用 fieldKey 从平铺 loginData 取值
            if (value == null) {
                value = loginData.get(field.getFieldKey());
            }
            if (value != null) {
                ctx.put(field.getFieldKey(), value);
            }
        }

        userContextCacheService.save(jti, ctx);
        log.info("存储用户上下文: jti={} keys={} values={}", jti, ctx.keySet(), ctx);
    }

    /**
     * 按 dot-notation 路径从 Map 中取值，如 "userId" 或 "data.esusMobile"。
     */
    @SuppressWarnings("unchecked")
    private Object navigateDotPath(Map<String, Object> data, String path) {
        String[] parts = path.split("\\.");
        Object current = data;
        for (String part : parts) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(part);
            if (current == null) return null;
        }
        return current;
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
