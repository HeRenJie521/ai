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
}
