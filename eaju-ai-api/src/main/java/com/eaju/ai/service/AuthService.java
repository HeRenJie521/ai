package com.eaju.ai.service;

import com.eaju.ai.dto.auth.LoginRequestDto;
import com.eaju.ai.dto.auth.LoginResponseDto;
import com.eaju.ai.dto.auth.LoginSessionSnapshot;
import com.eaju.ai.security.JwtIssueResult;
import com.eaju.ai.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * 通过 DMS 外部接口校验账号，签发本系统 JWT；业务用户标识为手机号。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final DmsExternalLoginClient dmsExternalLoginClient;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginSessionCacheService loginSessionCacheService;
    private final UserContextFieldService userContextFieldService;
    private final UserContextCacheService userContextCacheService;
    private final Set<String> adminPhones;

    public AuthService(
            DmsExternalLoginClient dmsExternalLoginClient,
            JwtTokenProvider jwtTokenProvider,
            LoginSessionCacheService loginSessionCacheService,
            UserContextFieldService userContextFieldService,
            UserContextCacheService userContextCacheService,
            @Value("${app.auth.admin-phones:}") String adminPhonesRaw) {
        this.dmsExternalLoginClient = dmsExternalLoginClient;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginSessionCacheService = loginSessionCacheService;
        this.userContextFieldService = userContextFieldService;
        this.userContextCacheService = userContextCacheService;
        this.adminPhones = parseAdminPhones(adminPhonesRaw);
    }

    public void logout(String jti) {
        if (StringUtils.hasText(jti)) {
            loginSessionCacheService.delete(jti.trim());
        }
    }

    private static Set<String> parseAdminPhones(String raw) {
        Set<String> set = new HashSet<String>();
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

    public LoginResponseDto login(LoginRequestDto request) {
        String phone = request.getPhone() != null ? request.getPhone().trim() : "";
        if (!StringUtils.hasText(phone)) {
            throw new IllegalArgumentException("手机号不能为空");
        }
        JsonNode root;
        try {
            root = dmsExternalLoginClient.login(phone, request.getPassword());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("登录处理失败: " + ex.getMessage());
        }
        if (!isLoginSuccess(root)) {
            String snippet = root != null ? root.toString() : "";
            log.warn(
                    "DMS appUserLogin 响应未命中成功规则，将拒绝登录。响应前 600 字: {}",
                    snippet.substring(0, Math.min(600, snippet.length())));
            throw new IllegalArgumentException(resolveErrorMessage(root));
        }
        String phoneFromApi = resolvePhone(root, phone);
        if (!StringUtils.hasText(phoneFromApi)) {
            phoneFromApi = phone;
        }
        String displayName = resolveDisplayName(root, phoneFromApi);
        boolean admin = adminPhones.contains(normalizePhone(phone));

        JwtIssueResult issued = jwtTokenProvider.createToken(phoneFromApi, displayName, admin);
        LoginSessionSnapshot snap = new LoginSessionSnapshot();
        snap.setPhone(phoneFromApi);
        snap.setUsername(displayName);
        snap.setAdmin(admin);
        snap.setIssuedAtEpochMs(System.currentTimeMillis());
        // 存完整 JSON 供字段解析；截断版仅用于日志诊断
        String dmsFullJson = root.toString();
        snap.setDmsResponseExcerpt(dmsFullJson);
        loginSessionCacheService.save(issued.getJti(), snap);

        // 按用户数据字段配置，从 DMS 登录响应中提取上下文并缓存
        try {
            Map<String, Object> ctx = userContextFieldService.extractContext(dmsFullJson);
            if (!ctx.isEmpty()) {
                userContextCacheService.save(issued.getJti(), ctx);
            }
        } catch (Exception ex) {
            log.warn("提取用户上下文失败，不影响登录: {}", ex.getMessage());
        }

        LoginResponseDto dto = new LoginResponseDto();
        dto.setToken(issued.getToken());
        dto.setJti(issued.getJti());
        dto.setExpiresIn(jwtTokenProvider.getExpirationSeconds());
        dto.setUserId(phoneFromApi);
        dto.setPhone(phoneFromApi);
        dto.setUsername(displayName);
        dto.setAdmin(admin);
        return dto;
    }

    private static String truncateJson(JsonNode root, int maxLen) {
        if (root == null) {
            return "";
        }
        String s = root.toString();
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen) + "…";
    }

    private static boolean isLoginSuccess(JsonNode root) {
        if (root == null || root.isNull()) {
            return false;
        }
        // DMS：根节点 returnCode=200（字符串或数字）即登录成功
        Boolean returnCodeOk = returnCodeIndicatesSuccess(root);
        if (Boolean.FALSE.equals(returnCodeOk)) {
            return false;
        }
        if (Boolean.TRUE.equals(returnCodeOk)) {
            return true;
        }
        if (isExplicitLoginFailure(root)) {
            return false;
        }
        if (root.path("success").asBoolean(false)) {
            return true;
        }
        JsonNode code = findFieldIgnoreCase(root, "code");
        if (code != null && !code.isNull()) {
            if (code.isIntegralNumber() && isSuccessIntCode(code.intValue())) {
                return true;
            }
            if (code.isTextual()) {
                String c = code.asText().trim();
                if ("0".equals(c) || "200".equals(c) || "0000".equals(c) || "true".equalsIgnoreCase(c)) {
                    return true;
                }
            }
        }
        JsonNode status = findFieldIgnoreCase(root, "status");
        if (status != null && !status.isNull()) {
            if (status.isIntegralNumber() && status.intValue() == 200) {
                return true;
            }
            if (status.isTextual() && "200".equals(status.asText().trim())) {
                return true;
            }
        }
        JsonNode retCode = findFieldIgnoreCase(root, "retCode");
        if (retCode != null && retCode.isIntegralNumber() && retCode.intValue() == 0) {
            return true;
        }
        String state = textIgnoreCase(root, "state");
        if ("ok".equalsIgnoreCase(state) || "success".equalsIgnoreCase(state)) {
            return true;
        }
        JsonNode data = findFieldIgnoreCase(root, "data");
        if (data != null && data.isObject()) {
            JsonNode resultNode = findFieldIgnoreCase(root, "result");
            if (resultNode != null && resultNode.isTextual()
                    && ("fail".equalsIgnoreCase(resultNode.asText()) || "error".equalsIgnoreCase(resultNode.asText()))) {
                return false;
            }
            if (data.has("token") || data.has("userId") || data.has("phone") || data.has("mobile")) {
                return true;
            }
            // 常见：data 里嵌套 user / userInfo，或仅有业务字段但 HTTP 已 2xx 且无明文失败
            if (data.size() > 0) {
                return true;
            }
        }
        if (data != null && data.isArray() && data.size() > 0) {
            return true;
        }
        return false;
    }

    /**
     * DMS：根节点 returnCode。存在且为 200/0（字符串或数字）视为成功；存在且为其它值视为失败；无该字段返回 null 交由其它规则。
     */
    private static Boolean returnCodeIndicatesSuccess(JsonNode root) {
        JsonNode n = findFieldIgnoreCase(root, "returnCode");
        if (n == null || n.isNull() || n.isMissingNode()) {
            return null;
        }
        if (n.isTextual()) {
            String t = n.asText().trim();
            if (!StringUtils.hasText(t)) {
                return null;
            }
            if ("200".equals(t) || "0".equals(t) || "0000".equals(t)) {
                return true;
            }
            return false;
        }
        if (n.isIntegralNumber()) {
            int v = n.intValue();
            if (v == 200 || v == 0) {
                return true;
            }
            return false;
        }
        if (n.isBoolean()) {
            return n.asBoolean();
        }
        return null;
    }

    private static boolean isExplicitLoginFailure(JsonNode root) {
        Boolean returnCodeOk = returnCodeIndicatesSuccess(root);
        if (Boolean.FALSE.equals(returnCodeOk)) {
            return true;
        }
        if (root.path("success").isBoolean() && !root.path("success").asBoolean()) {
            return true;
        }
        JsonNode resultNode = findFieldIgnoreCase(root, "result");
        if (resultNode != null && resultNode.isTextual()) {
            String result = resultNode.asText().trim();
            if ("fail".equalsIgnoreCase(result) || "error".equalsIgnoreCase(result)) {
                return true;
            }
        }
        JsonNode code = findFieldIgnoreCase(root, "code");
        if (code != null && !code.isNull()) {
            if (code.isIntegralNumber()) {
                int v = code.intValue();
                if (v == -1 || v == 400 || v == 401 || v == 403 || v == 500) {
                    return true;
                }
            }
            if (code.isTextual()) {
                String s = code.asText().trim();
                if ("-1".equals(s) || "400".equals(s) || "401".equals(s) || "403".equals(s) || "500".equals(s)) {
                    return true;
                }
            }
        }
        JsonNode data = findFieldIgnoreCase(root, "data");
        if (data != null && data.isObject()) {
            JsonNode err = findFieldIgnoreCase(data, "error");
            if (err != null && err.isTextual() && StringUtils.hasText(err.asText())) {
                return true;
            }
            JsonNode errMsg = findFieldIgnoreCase(data, "errorMsg");
            if (errMsg != null && errMsg.isTextual() && StringUtils.hasText(errMsg.asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSuccessIntCode(int v) {
        return v == 0 || v == 200;
    }

    private static JsonNode findFieldIgnoreCase(JsonNode root, String target) {
        if (root == null || !root.isObject()) {
            return null;
        }
        Iterator<String> it = root.fieldNames();
        while (it.hasNext()) {
            String name = it.next();
            if (target.equalsIgnoreCase(name)) {
                return root.get(name);
            }
        }
        return null;
    }

    private static String textIgnoreCase(JsonNode root, String target) {
        JsonNode n = findFieldIgnoreCase(root, target);
        return n != null && n.isTextual() ? n.asText().trim() : "";
    }

    private static String resolveErrorMessage(JsonNode root) {
        for (String key : Arrays.asList("info", "msg", "message", "errorMsg", "errMsg", "error")) {
            JsonNode n = findFieldIgnoreCase(root, key);
            if (n != null && n.isTextual() && StringUtils.hasText(n.asText())) {
                return n.asText().trim();
            }
            n = root.path(key);
            if (n.isTextual() && StringUtils.hasText(n.asText())) {
                return n.asText().trim();
            }
        }
        JsonNode data = findFieldIgnoreCase(root, "data");
        if (data != null && data.isObject()) {
            for (String key : Arrays.asList("info", "msg", "message", "errorMsg", "errMsg", "error")) {
                JsonNode n = findFieldIgnoreCase(data, key);
                if (n != null && n.isTextual() && StringUtils.hasText(n.asText())) {
                    return n.asText().trim();
                }
            }
        }
        return "登录失败（服务端未识别 DMS 成功标识，请查看接口返回 JSON 与日志）";
    }

    private static String resolvePhone(JsonNode root, String requestPhone) {
        JsonNode result = findFieldIgnoreCase(root, "result");
        if (result != null && result.isObject()) {
            String flat = tryPhoneOnObject(result);
            if (flat != null) {
                return flat;
            }
        }
        JsonNode data = findFieldIgnoreCase(root, "data");
        if (data != null && data.isObject()) {
            String flat = tryPhoneOnObject(data);
            if (flat != null) {
                return flat;
            }
            JsonNode teachInData = findFieldIgnoreCase(data, "appTeachInfoData");
            if (teachInData != null && teachInData.isObject()) {
                String p = tryPhoneOnObject(teachInData);
                if (p != null) {
                    return p;
                }
            }
            for (String nested : Arrays.asList("user", "userInfo", "appUser", "User", "member", "accountInfo")) {
                JsonNode sub = findFieldIgnoreCase(data, nested);
                if (sub != null && sub.isObject()) {
                    String p = tryPhoneOnObject(sub);
                    if (p != null) {
                        return p;
                    }
                }
            }
        }
        JsonNode teach = findFieldIgnoreCase(root, "appTeachInfoData");
        if (teach != null && teach.isObject()) {
            String p = tryPhoneOnObject(teach);
            if (p != null) {
                return p;
            }
        }
        if (root != null && root.isObject()) {
            String p = tryPhoneOnObject(root);
            if (p != null) {
                return p;
            }
        }
        return requestPhone != null ? requestPhone.trim() : "";
    }

    private static String tryPhoneOnObject(JsonNode obj) {
        if (obj == null || !obj.isObject()) {
            return null;
        }
        for (String key : Arrays.asList(
                "PHONE",
                "phone",
                "mobile",
                "MOBILE",
                "esusMobile",
                "esusLoginName",
                "userPhone",
                "account",
                "tel",
                "msisdn",
                "loginPhone")) {
            JsonNode n = findFieldIgnoreCase(obj, key);
            if (n != null && n.isTextual() && StringUtils.hasText(n.asText())) {
                return n.asText().trim();
            }
            n = obj.get(key);
            if (n != null && n.isTextual() && StringUtils.hasText(n.asText())) {
                return n.asText().trim();
            }
        }
        return null;
    }

    private static String normalizePhone(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.replaceAll("\\D", "");
    }

    private static String resolveDisplayName(JsonNode root, String fallbackPhone) {
        JsonNode result = findFieldIgnoreCase(root, "result");
        if (result != null && result.isObject()) {
            for (String key : Arrays.asList("NAME", "name", "nickName", "nickname", "userName", "realName")) {
                JsonNode n = findFieldIgnoreCase(result, key);
                if (n != null && n.isTextual() && StringUtils.hasText(n.asText())) {
                    return n.asText().trim();
                }
            }
        }
        JsonNode data = findFieldIgnoreCase(root, "data");
        if (data != null && data.isObject()) {
            JsonNode cn = findFieldIgnoreCase(data, "esusUserNameCn");
            if (cn != null && cn.isTextual() && StringUtils.hasText(cn.asText())) {
                return cn.asText().trim();
            }
            for (String key : Arrays.asList("nickName", "nickname", "userName", "name", "realName")) {
                JsonNode n = findFieldIgnoreCase(data, key);
                if (n != null && n.isTextual() && StringUtils.hasText(n.asText())) {
                    return n.asText().trim();
                }
            }
            for (String nested : Arrays.asList("user", "userInfo", "appUser", "User")) {
                JsonNode sub = findFieldIgnoreCase(data, nested);
                if (sub != null && sub.isObject()) {
                    for (String key : Arrays.asList("nickName", "nickname", "userName", "name", "realName")) {
                        JsonNode n = findFieldIgnoreCase(sub, key);
                        if (n != null && n.isTextual() && StringUtils.hasText(n.asText())) {
                            return n.asText().trim();
                        }
                    }
                }
            }
            JsonNode teach = findFieldIgnoreCase(data, "appTeachInfoData");
            if (teach != null && teach.isObject()) {
                JsonNode ebteName = findFieldIgnoreCase(teach, "ebteName");
                if (ebteName != null && ebteName.isTextual() && StringUtils.hasText(ebteName.asText())) {
                    return ebteName.asText().trim();
                }
            }
        }
        JsonNode teachRoot = findFieldIgnoreCase(root, "appTeachInfoData");
        if (teachRoot != null && teachRoot.isObject()) {
            JsonNode ebteName = findFieldIgnoreCase(teachRoot, "ebteName");
            if (ebteName != null && ebteName.isTextual() && StringUtils.hasText(ebteName.asText())) {
                return ebteName.asText().trim();
            }
        }
        return fallbackPhone;
    }
}
