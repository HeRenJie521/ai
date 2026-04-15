package com.eaju.ai.service;

import com.eaju.ai.dto.auth.LoginResponseDto;
import com.eaju.ai.dto.auth.LoginSessionSnapshot;
import com.eaju.ai.dto.embed.EmbedLoginRequestDto;
import com.eaju.ai.persistence.entity.AiAppEntity;
import com.eaju.ai.persistence.entity.ApiKeyEntity;
import com.eaju.ai.persistence.repository.AiAppRepository;
import com.eaju.ai.persistence.repository.ApiKeyRepository;
import com.eaju.ai.security.JwtIssueResult;
import com.eaju.ai.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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

    public EmbedAuthService(ApiKeyRepository apiKeyRepository,
                            AiAppRepository aiAppRepository,
                            JwtTokenProvider jwtTokenProvider,
                            LoginSessionCacheService loginSessionCacheService) {
        this.apiKeyRepository = apiKeyRepository;
        this.aiAppRepository = aiAppRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginSessionCacheService = loginSessionCacheService;
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

        // 从关联的 AI 应用获取默认模型
        String defaultModel = null;
        if (integration.getAppId() != null) {
            java.util.Optional<AiAppEntity> appOpt =
                    aiAppRepository.findByIdAndDeletedIsFalse(integration.getAppId());
            if (appOpt.isPresent()) {
                defaultModel = appOpt.get().getModelId();
            }
        }

        LoginResponseDto dto = new LoginResponseDto();
        dto.setToken(issued.getToken());
        dto.setJti(issued.getJti());
        dto.setExpiresIn(jwtTokenProvider.getExpirationSeconds());
        dto.setUserId(userId);
        dto.setPhone(userId);
        dto.setUsername(displayName);
        dto.setAdmin(false);
        dto.setDefaultModel(defaultModel);
        dto.setIntegrationName(integration.getName());
        return dto;
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
