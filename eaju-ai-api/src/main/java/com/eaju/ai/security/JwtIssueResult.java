package com.eaju.ai.security;

/** 签发 JWT 与标准 jti（{@link io.jsonwebtoken.JwtBuilder#setId}） */
public final class JwtIssueResult {

    private final String token;
    private final String jti;

    public JwtIssueResult(String token, String jti) {
        this.token = token;
        this.jti = jti;
    }

    public String getToken() {
        return token;
    }

    public String getJti() {
        return jti;
    }
}
