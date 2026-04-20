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
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import java.util.stream.Collectors;

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
    private final UserContextFieldService userContextFieldService;
    private final DmsExternalLoginClient dmsExternalLoginClient;
    private final Set<String> adminPhones;

    public EmbedAuthService(ApiKeyRepository apiKeyRepository,
                            AiAppRepository aiAppRepository,
                            JwtTokenProvider jwtTokenProvider,
                            LoginSessionCacheService loginSessionCacheService,
                            UserContextFieldRepository userContextFieldRepository,
                            UserContextCacheService userContextCacheService,
                            UserContextFieldService userContextFieldService,
                            DmsExternalLoginClient dmsExternalLoginClient,
                            @Value("${app.auth.admin-phones:}") String adminPhonesRaw) {
        this.apiKeyRepository = apiKeyRepository;
        this.aiAppRepository = aiAppRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginSessionCacheService = loginSessionCacheService;
        this.userContextFieldRepository = userContextFieldRepository;
        this.userContextCacheService = userContextCacheService;
        this.userContextFieldService = userContextFieldService;
        this.dmsExternalLoginClient = dmsExternalLoginClient;
        this.adminPhones = parseAdminPhones(adminPhonesRaw);
        log.info("EmbedAuthService 初始化：adminPhones 配置原始值='{}'，解析后={}", adminPhonesRaw, this.adminPhones);
    }

    private static Set<String> parseAdminPhones(String raw) {
        Set<String> set = new HashSet<>();
        if (!StringUtils.hasText(raw)) {
            return set;
        }
        for (String p : raw.split(",")) {
            if (StringUtils.hasText(p)) {
                String normalized = normalizePhone(p);
                if (StringUtils.hasText(normalized)) {
                    set.add(normalized);
                }
            }
        }
        return set;
    }

    private static String normalizePhone(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.replaceAll("\\D", "");
    }

    public LoginResponseDto embedLogin(EmbedLoginRequestDto req) {
        String userId = req.getUserId() != null ? req.getUserId().trim() : "";
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("用户 ID 不能为空");
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
            log.warn("EmbedLogin 凭证不匹配：integrationId={}, userId={}", req.getIntegrationId(), userId);
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

        log.info("EmbedLogin 成功：integrationId={}, userId={}", integration.getId(), userId);

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
     * 应用管理嵌入登录：通过 DMS 登录接口验证用户，缓存用户数据字段。
     * <p>
     * 登录流程：
     * 1. 调用 DMS appUserLogin 接口验证手机号
     * 2. 登录失败则抛出异常，前端功能不可用
     * 3. 登录成功后，根据 user_context_field 配置解析用户数据字段
     * 4. 将解析后的 key-value 缓存到 Redis（ctx:{jti}）
     * 5. 返回缓存的 key-value 给前端（仅用于测试展示）
     */
    public LoginResponseDto appEmbedLogin(AppEmbedLoginRequestDto req) {
        String userId = req.getUserId() != null ? req.getUserId().trim() : "";
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }

        AiAppEntity app = aiAppRepository.findByIdAndDeletedIsFalse(req.getAppId())
                .orElseThrow(() -> new IllegalArgumentException("AI 应用不存在或已删除"));

        // 调用 DMS 登录接口（loginType=2 为免密登录）
        JsonNode dmsResponse;
        try {
            dmsResponse = dmsExternalLoginClient.loginWithLoginType(userId, "2");
        } catch (Exception ex) {
            log.warn("AppEmbedLogin: DMS 登录失败：appId={}, userId={}, error={}", app.getId(), userId, ex.getMessage());
            throw new IllegalArgumentException("登录失败：" + ex.getMessage());
        }

        // 验证 DMS 登录是否成功
        if (!isDmsLoginSuccess(dmsResponse)) {
            String errorMsg = extractDmsErrorMessage(dmsResponse);
            log.warn("AppEmbedLogin: DMS 登录失败：appId={}, userId={}, error={}", app.getId(), userId, errorMsg);
            throw new IllegalArgumentException("登录失败：" + errorMsg);
        }

        // 从 DMS 响应中解析手机号和用户名
        String phoneFromDms = AuthService.resolvePhone(dmsResponse, userId);
        String displayName = AuthService.resolveDisplayName(dmsResponse, phoneFromDms);
        if (StringUtils.hasText(req.getUsername())) {
            displayName = req.getUsername().trim();
        }

        // 签发 JWT
        JwtIssueResult issued = jwtTokenProvider.createAppEmbedToken(phoneFromDms, displayName, app.getId());

        // 保存登录会话快照
        LoginSessionSnapshot snap = new LoginSessionSnapshot();
        snap.setPhone(phoneFromDms);
        snap.setUsername(displayName);
        // 判断是否为管理员
        boolean admin = adminPhones.contains(normalizePhone(phoneFromDms));
        snap.setAdmin(admin);
        snap.setIssuedAtEpochMs(System.currentTimeMillis());
        snap.setDmsResponseExcerpt(dmsResponse.toString());
        loginSessionCacheService.save(issued.getJti(), snap);

        // 根据用户数据字段配置，从 DMS 登录响应中提取上下文并缓存
        Map<String, Object> userContext = new HashMap<>();
        try {
            userContext = userContextFieldService.extractContext(dmsResponse.toString());
            // 合并 extraContext
            if (req.getExtraContext() != null && !req.getExtraContext().isEmpty()) {
                // 仅将白名单字段存入缓存
                List<UserContextFieldEntity> allowedFields = userContextFieldRepository.findByEnabledIsTrueOrderByIdAsc();
                List<String> allowedKeys = allowedFields.stream()
                        .map(UserContextFieldEntity::getFieldKey)
                        .collect(Collectors.toList());
                for (Map.Entry<String, Object> entry : req.getExtraContext().entrySet()) {
                    if (allowedKeys.contains(entry.getKey())) {
                        userContext.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            if (!userContext.isEmpty()) {
                userContextCacheService.save(issued.getJti(), userContext);
                log.info("AppEmbedLogin: 缓存用户上下文：jti={} keys={} values={}", issued.getJti(), userContext.keySet(), userContext);
            }
        } catch (Exception ex) {
            log.warn("AppEmbedLogin: 提取用户上下文失败：{}", ex.getMessage());
        }

        log.info("AppEmbedLogin 成功：appId={}, userId={}, phone={}, username={}", app.getId(), userId, phoneFromDms, displayName);

        LoginResponseDto dto = new LoginResponseDto();
        dto.setToken(issued.getToken());
        dto.setJti(issued.getJti());
        dto.setExpiresIn(jwtTokenProvider.getExpirationSeconds());
        dto.setUserId(phoneFromDms);
        dto.setPhone(phoneFromDms);
        dto.setUsername(displayName);
        dto.setAdmin(admin);
        dto.setDefaultModel(app.getLlmModelId() != null ? String.valueOf(app.getLlmModelId()) : null);
        dto.setIntegrationName(app.getName());
        // 返回缓存的 key-value，仅用于前端测试展示
        dto.setUserContext(userContext);
        return dto;
    }

    /**
     * 判断 DMS 登录响应是否成功。
     */
    private boolean isDmsLoginSuccess(JsonNode root) {
        if (root == null || root.isNull()) {
            return false;
        }
        // DMS：根节点 returnCode=200（字符串或数字）即登录成功
        JsonNode returnCodeNode = findFieldIgnoreCase(root, "returnCode");
        if (returnCodeNode != null && !returnCodeNode.isNull()) {
            if (returnCodeNode.isTextual()) {
                String rc = returnCodeNode.asText().trim();
                return "200".equals(rc) || "0".equals(rc) || "0000".equals(rc);
            }
            if (returnCodeNode.isIntegralNumber()) {
                int rc = returnCodeNode.intValue();
                return rc == 200 || rc == 0;
            }
        }
        // 兼容其他成功标识
        if (root.path("success").asBoolean(false)) {
            return true;
        }
        JsonNode codeNode = findFieldIgnoreCase(root, "code");
        if (codeNode != null && !codeNode.isNull()) {
            if (codeNode.isIntegralNumber() && (codeNode.intValue() == 0 || codeNode.intValue() == 200)) {
                return true;
            }
            if (codeNode.isTextual()) {
                String c = codeNode.asText().trim();
                return "0".equals(c) || "200".equals(c) || "0000".equals(c);
            }
        }
        return false;
    }

    /**
     * 提取 DMS 登录失败错误消息。
     */
    private String extractDmsErrorMessage(JsonNode root) {
        if (root == null) {
            return "登录失败（无响应）";
        }
        // 尝试常见错误字段
        for (String key : new String[]{"info", "msg", "message", "errorMsg", "errMsg", "error", "returnMsg"}) {
            JsonNode n = findFieldIgnoreCase(root, key);
            if (n != null && n.isTextual() && StringUtils.hasText(n.asText())) {
                return n.asText().trim();
            }
        }
        // 尝试 data.errorMsg
        JsonNode data = findFieldIgnoreCase(root, "data");
        if (data != null && data.isObject()) {
            for (String key : new String[]{"errorMsg", "error", "msg", "message"}) {
                JsonNode n = findFieldIgnoreCase(data, key);
                if (n != null && n.isTextual() && StringUtils.hasText(n.asText())) {
                    return n.asText().trim();
                }
            }
        }
        return "登录失败（未识别 DMS 错误信息）";
    }

    private static JsonNode findFieldIgnoreCase(JsonNode root, String target) {
        if (root == null || !root.isObject()) {
            return null;
        }
        java.util.Iterator<String> it = root.fieldNames();
        while (it.hasNext()) {
            String name = it.next();
            if (target.equalsIgnoreCase(name)) {
                return root.get(name);
            }
        }
        return null;
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
        log.info("存储用户上下文：jti={} keys={} values={}", jti, ctx.keySet(), ctx);
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
