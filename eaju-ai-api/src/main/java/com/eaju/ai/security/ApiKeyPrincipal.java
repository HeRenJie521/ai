package com.eaju.ai.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * X-API-Key 鉴权主体；业务 userId 使用 {@link #getVirtualUserId()}（写入 chat 会话隔离）。
 */
public class ApiKeyPrincipal implements UserDetails {

    public static final String ROLE_API_KEY = "ROLE_API_KEY";

    private final Long id;
    private final String keyName;
    private final String plainKey;
    private final Long appId;

    public ApiKeyPrincipal(Long id, String keyName, String plainKey, Long appId) {
        this.id = id;
        this.keyName = keyName != null ? keyName : "";
        this.plainKey = plainKey != null ? plainKey : "";
        this.appId = appId;
    }

    public Long getId() { return id; }
    public Long getAppId() { return appId; }

    public String getKeyName() {
        return keyName;
    }

    /** 原始 API Key 明文，用作 chat_conversation.user_id 以标识来源 */
    public String getPlainKey() {
        return plainKey;
    }

    /** 兼容旧逻辑保留（Redis 隔离等） */
    public String getVirtualUserId() {
        return "apikey:" + id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(ROLE_API_KEY));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return getVirtualUserId();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
