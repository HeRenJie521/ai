package com.eaju.ai.service;

import com.eaju.ai.dto.auth.LoginResponseDto;
import com.eaju.ai.dto.auth.LoginSessionSnapshot;
import com.eaju.ai.persistence.repository.AdminAccountRepository;
import com.eaju.ai.security.JwtIssueResult;
import com.eaju.ai.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 移动端免密登录服务：通过手机号调用 DMS 免密接口（loginType=2），签发本系统 JWT。
 */
@Service
public class MobileAuthService {

    private static final Logger log = LoggerFactory.getLogger(MobileAuthService.class);

    private final DmsExternalLoginClient dmsExternalLoginClient;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginSessionCacheService loginSessionCacheService;
    private final UserContextFieldService userContextFieldService;
    private final UserContextCacheService userContextCacheService;
    private final AdminAccountRepository adminAccountRepository;

    public MobileAuthService(
            DmsExternalLoginClient dmsExternalLoginClient,
            JwtTokenProvider jwtTokenProvider,
            LoginSessionCacheService loginSessionCacheService,
            UserContextFieldService userContextFieldService,
            UserContextCacheService userContextCacheService,
            AdminAccountRepository adminAccountRepository) {
        this.dmsExternalLoginClient = dmsExternalLoginClient;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginSessionCacheService = loginSessionCacheService;
        this.userContextFieldService = userContextFieldService;
        this.userContextCacheService = userContextCacheService;
        this.adminAccountRepository = adminAccountRepository;
    }

    public LoginResponseDto mobileLogin(String phone) {
        phone = phone.trim();
        if (!StringUtils.hasText(phone)) {
            throw new IllegalArgumentException("手机号不能为空");
        }

        JsonNode dmsResponse;
        try {
            dmsResponse = dmsExternalLoginClient.loginWithLoginType(phone, "2");
        } catch (Exception ex) {
            log.warn("MobileLogin: DMS 登录失败：phone={}, error={}", phone, ex.getMessage());
            throw new IllegalArgumentException("登录失败：" + ex.getMessage());
        }

        if (!isDmsLoginSuccess(dmsResponse)) {
            log.warn("MobileLogin: DMS 返回失败：phone={}, response={}", phone,
                    dmsResponse != null ? dmsResponse.toString().substring(0, Math.min(200, dmsResponse.toString().length())) : "null");
            throw new IllegalArgumentException("登录失败：手机号验证未通过");
        }

        String phoneFromDms = AuthService.resolvePhone(dmsResponse, phone);
        if (!StringUtils.hasText(phoneFromDms)) {
            phoneFromDms = phone;
        }
        String displayName = AuthService.resolveDisplayName(dmsResponse, phoneFromDms);
        boolean admin = isAdmin(phoneFromDms);

        JwtIssueResult issued = jwtTokenProvider.createToken(phoneFromDms, displayName, admin);

        LoginSessionSnapshot snap = new LoginSessionSnapshot();
        snap.setPhone(phoneFromDms);
        snap.setUsername(displayName);
        snap.setAdmin(admin);
        snap.setIssuedAtEpochMs(System.currentTimeMillis());
        snap.setDmsResponseExcerpt(dmsResponse.toString());
        loginSessionCacheService.save(issued.getJti(), snap);

        try {
            Map<String, Object> ctx = userContextFieldService.extractContext(dmsResponse.toString());
            if (!ctx.isEmpty()) {
                userContextCacheService.save(issued.getJti(), ctx);
            }
        } catch (Exception ex) {
            log.warn("MobileLogin: 提取用户上下文失败，不影响登录: {}", ex.getMessage());
        }

        log.info("MobileLogin 成功：phone={}, username={}", phoneFromDms, displayName);

        LoginResponseDto dto = new LoginResponseDto();
        dto.setToken(issued.getToken());
        dto.setJti(issued.getJti());
        dto.setExpiresIn(jwtTokenProvider.getExpirationSeconds());
        dto.setUserId(phoneFromDms);
        dto.setPhone(phoneFromDms);
        dto.setUsername(displayName);
        dto.setAdmin(admin);
        return dto;
    }

    private boolean isAdmin(String phone) {
        if (!StringUtils.hasText(phone)) return false;
        try {
            return adminAccountRepository.existsByPhone(phone.replaceAll("\\D", ""));
        } catch (Exception e) {
            log.warn("查询管理员状态失败：{}", e.getMessage());
            return false;
        }
    }

    private boolean isDmsLoginSuccess(JsonNode root) {
        if (root == null || root.isNull()) return false;
        JsonNode returnCode = findFieldIgnoreCase(root, "returnCode");
        if (returnCode != null && !returnCode.isNull()) {
            if (returnCode.isTextual()) {
                String rc = returnCode.asText().trim();
                return "200".equals(rc) || "0".equals(rc) || "0000".equals(rc);
            }
            if (returnCode.isIntegralNumber()) {
                int rc = returnCode.intValue();
                return rc == 200 || rc == 0;
            }
        }
        if (root.path("success").asBoolean(false)) return true;
        JsonNode data = findFieldIgnoreCase(root, "data");
        if (data != null && (data.isObject() || data.isArray()) && data.size() > 0) return true;
        return false;
    }

    private static JsonNode findFieldIgnoreCase(JsonNode root, String target) {
        if (root == null || !root.isObject()) return null;
        java.util.Iterator<String> it = root.fieldNames();
        while (it.hasNext()) {
            String name = it.next();
            if (target.equalsIgnoreCase(name)) return root.get(name);
        }
        return null;
    }
}
