package com.eaju.ai.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 登录主体：手机号为业务标识；{@code jti} 与 Redis 登录会话一致。
 */
public class AuthUserPrincipal implements UserDetails {

    private final String phone;
    private final String displayName;
    private final boolean admin;
    private final boolean enabled;
    /** JWT jti，与 {@link com.eaju.ai.service.LoginSessionCacheService} 键一致 */
    private final String jti;
    /**
     * WEB_EMBED 免密登录时携带，表示通过哪个集成登录；普通 JWT 登录为 null。
     * 写入 chat_turn.integration_id / chat_conversation.integration_id 以供统计。
     */
    private final Long integrationId;

    /**
     * 应用管理嵌入登录时携带，表示直接通过哪个 AI 应用登录；WEB_EMBED 或普通登录为 null。
     */
    private final Long appId;

    public AuthUserPrincipal(String phone, String displayName, boolean admin, boolean enabled, String jti) {
        this(phone, displayName, admin, enabled, jti, null, null);
    }

    public AuthUserPrincipal(String phone, String displayName, boolean admin, boolean enabled, String jti, Long integrationId) {
        this(phone, displayName, admin, enabled, jti, integrationId, null);
    }

    public AuthUserPrincipal(String phone, String displayName, boolean admin, boolean enabled, String jti, Long integrationId, Long appId) {
        this.phone = phone != null ? phone.trim() : "";
        this.displayName = displayName != null ? displayName : this.phone;
        this.admin = admin;
        this.enabled = enabled;
        this.jti = jti != null ? jti : "";
        this.integrationId = integrationId;
        this.appId = appId;
    }

    public String getPhone() {
        return phone;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getJti() {
        return jti;
    }

    public boolean isAdmin() {
        return admin;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> list = new ArrayList<GrantedAuthority>();
        list.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (admin) {
            list.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return list;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return phone;
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
        return enabled;
    }

    public Long getIntegrationId() {
        return integrationId;
    }

    public Long getAppId() {
        return appId;
    }

    /** 会话是否带 jti（旧 token 无 jti 时仅校验 JWT，不查 Redis） */
    public boolean hasSessionId() {
        return StringUtils.hasText(jti);
    }
}
