package com.eaju.ai.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * 从 {@link Authentication} 解析 JWT 用户或 API Key 对应的业务 userId。
 */
public final class CallerPrincipal {

    private CallerPrincipal() {
    }

    public static String userId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Object p = authentication.getPrincipal();
        if (p instanceof AuthUserPrincipal) {
            return ((AuthUserPrincipal) p).getPhone();
        }
        if (p instanceof ApiKeyPrincipal) {
            return ((ApiKeyPrincipal) p).getPlainKey();
        }
        return null;
    }

    public static Long apiKeyId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Object p = authentication.getPrincipal();
        if (p instanceof ApiKeyPrincipal) {
            return ((ApiKeyPrincipal) p).getId();
        }
        return null;
    }

    /**
     * WEB_EMBED 免密登录时 JWT 携带的集成 ID；普通登录返回 null。
     */
    public static Long integrationId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Object p = authentication.getPrincipal();
        if (p instanceof AuthUserPrincipal) {
            return ((AuthUserPrincipal) p).getIntegrationId();
        }
        return null;
    }

    /**
     * 应用管理嵌入登录时 JWT 携带的 AI 应用 ID；其他登录方式返回 null。
     */
    public static Long appId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Object p = authentication.getPrincipal();
        if (p instanceof AuthUserPrincipal) {
            return ((AuthUserPrincipal) p).getAppId();
        }
        return null;
    }

    /**
     * JWT jti（用于读取用户上下文缓存）；API Key 调用或匿名访问返回 null。
     */
    public static String jti(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Object p = authentication.getPrincipal();
        if (p instanceof AuthUserPrincipal) {
            String jti = ((AuthUserPrincipal) p).getJti();
            return org.springframework.util.StringUtils.hasText(jti) ? jti : null;
        }
        return null;
    }
}
