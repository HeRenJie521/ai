package com.eaju.ai.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationMs;
    private final long expirationSeconds;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-hours:168}") long expirationHours) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret 长度至少 32 字节（UTF-8）以满足 HS256");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationHours * 60L * 60L * 1000L;
        this.expirationSeconds = Math.max(1L, expirationHours * 3600L);
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    /**
     * subject = 手机号；标准 {@code jti} 通过 {@link io.jsonwebtoken.JwtBuilder#setId(String)} 写入。
     */
    public JwtIssueResult createToken(String phone, String displayName, boolean admin) {
        String jti = UUID.randomUUID().toString().replace("-", "");
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMs);
        String token = Jwts.builder()
                .setId(jti)
                .setSubject(phone)
                .claim("username", displayName != null ? displayName : phone)
                .claim("admin", admin)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
        return new JwtIssueResult(token, jti);
    }

    public Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 为 WEB_EMBED 免密登录颁发 JWT，附带 integrationId claim。
     */
    public JwtIssueResult createEmbedToken(String phone, String displayName, Long integrationId) {
        String jti = UUID.randomUUID().toString().replace("-", "");
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMs);
        String token = Jwts.builder()
                .setId(jti)
                .setSubject(phone)
                .claim("username", displayName != null ? displayName : phone)
                .claim("admin", false)
                .claim("integrationId", integrationId)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
        return new JwtIssueResult(token, jti);
    }

    public AuthUserPrincipal toPrincipal(Claims claims) {
        String phone = claims.getSubject();
        String username = claims.get("username", String.class);
        Boolean admin = claims.get("admin", Boolean.class);
        String jti = claims.getId();
        Long integrationId = null;
        Object iidRaw = claims.get("integrationId");
        if (iidRaw instanceof Number) {
            integrationId = ((Number) iidRaw).longValue();
        }
        return new AuthUserPrincipal(
                phone,
                username != null ? username : phone,
                Boolean.TRUE.equals(admin),
                true,
                jti,
                integrationId);
    }
}
