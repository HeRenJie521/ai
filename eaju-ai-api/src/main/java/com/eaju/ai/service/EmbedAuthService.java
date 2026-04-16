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

        // 提取并存储用户上下文字段（过滤白名单）
        storeUserContext(issued.getJti(), req.getExtraContext());

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
     * 根据 user_context_field 白名单过滤 extraContext，将允许的字段存入 Redis。
     */
    private void storeUserContext(String jti, Map<String, Object> extraContext) {
        if (!StringUtils.hasText(jti) || extraContext == null || extraContext.isEmpty()) {
            return;
        }
        List<UserContextFieldEntity> allowedFields = userContextFieldRepository.findByEnabledIsTrueOrderByIdAsc();
        if (allowedFields.isEmpty()) {
            return;
        }
        Map<String, Object> filtered = new HashMap<>();
        for (UserContextFieldEntity field : allowedFields) {
            if (extraContext.containsKey(field.getFieldKey())) {
                filtered.put(field.getFieldKey(), extraContext.get(field.getFieldKey()));
            }
        }
        if (!filtered.isEmpty()) {
            userContextCacheService.save(jti, filtered);
            log.debug("存储用户上下文: jti={} keys={}", jti, filtered.keySet());
        }
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
