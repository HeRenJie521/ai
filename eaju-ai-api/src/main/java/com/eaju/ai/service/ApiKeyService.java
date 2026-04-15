package com.eaju.ai.service;

import com.eaju.ai.persistence.entity.ApiKeyEntity;
import com.eaju.ai.persistence.repository.ApiKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

@Service
public class ApiKeyService {

    private static final String PREFIX = "eaju_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Transactional(readOnly = true)
    public List<ApiKeyEntity> listAll() {
        return apiKeyRepository.findByDeletedIsFalse();
    }

    @Transactional(readOnly = true)
    public Optional<ApiKeyEntity> findById(Long id) {
        return apiKeyRepository.findByIdAndDeletedIsFalse(id);
    }

    /**
     * 创建集成（支持 API_KEY 与 WEB_EMBED 两种类型）。
     *
     * @param type           1=API_KEY  2=WEB_EMBED
     * @param allowedOrigins WEB_EMBED 允许嵌入的来源域名（逗号分隔，null 表示不限）
     */
    @Transactional
    public CreatedApiKey create(String name, int type, String allowedOrigins) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("名称不能为空");
        }
        ApiKeyEntity e = new ApiKeyEntity();
        e.setName(name.trim());
        e.setType(type);
        e.setEnabled(true);

        if (type == 2) {
            // WEB_EMBED：emb_ + 28 hex chars
            String plain = "emb_" + randomHex(14).substring(0, 28);
            e.setSecretHash(sha256Hex(plain));
            e.setSecretPrefix(plain);
            if (StringUtils.hasText(allowedOrigins)) {
                e.setAllowedOrigins(allowedOrigins.trim());
            }
        } else {
            // API_KEY：PREFIX(5) + 27 hex chars
            String plain = PREFIX + randomHex(14).substring(0, 27);
            e.setSecretHash(sha256Hex(plain));
            e.setSecretPrefix(plain);
        }
        apiKeyRepository.save(e);
        return new CreatedApiKey(e, e.getSecretPrefix());
    }

    @Transactional
    public ApiKeyEntity update(Long id, String name, Boolean enabled, String allowedOrigins) {
        ApiKeyEntity e = apiKeyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("API Key 不存在"));
        if (StringUtils.hasText(name)) {
            e.setName(name.trim());
        }
        if (enabled != null) {
            e.setEnabled(enabled);
        }
        if (allowedOrigins != null) {
            e.setAllowedOrigins(StringUtils.hasText(allowedOrigins) ? allowedOrigins.trim() : null);
        }
        return apiKeyRepository.save(e);
    }

    @Transactional
    public void delete(Long id) {
        ApiKeyEntity e = apiKeyRepository.findByIdAndDeletedIsFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("API Key 不存在"));
        e.setDeleted(true);
        apiKeyRepository.save(e);
    }

    @Transactional(readOnly = true)
    public Optional<ApiKeyEntity> validatePlainKey(String plain) {
        if (!StringUtils.hasText(plain)) {
            return Optional.empty();
        }
        String hash = sha256Hex(plain.trim());
        // type=1 (API_KEY) only — prevents embed credentials from authenticating via X-API-Key header
        return apiKeyRepository.findBySecretHashAndTypeAndEnabledIsTrueAndDeletedIsFalse(hash, 1);
    }

    private static String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte b : buf) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
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

    public static final class CreatedApiKey {
        private final ApiKeyEntity entity;
        private final String plainSecret;

        public CreatedApiKey(ApiKeyEntity entity, String plainSecret) {
            this.entity = entity;
            this.plainSecret = plainSecret;
        }

        public ApiKeyEntity getEntity() { return entity; }
        public String getPlainSecret() { return plainSecret; }
    }
}
