package com.eaju.ai.dto.auth;

/**
 * 登录成功后写入 Redis 的快照（与 JWT jti 绑定）。
 */
public class LoginSessionSnapshot {

    private String phone;
    private String username;
    private boolean admin;
    private long issuedAtEpochMs;
    /** DMS 原始 JSON 截断保存，便于排障；勿存敏感过大字段时可缩短 */
    private String dmsResponseExcerpt;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public long getIssuedAtEpochMs() {
        return issuedAtEpochMs;
    }

    public void setIssuedAtEpochMs(long issuedAtEpochMs) {
        this.issuedAtEpochMs = issuedAtEpochMs;
    }

    public String getDmsResponseExcerpt() {
        return dmsResponseExcerpt;
    }

    public void setDmsResponseExcerpt(String dmsResponseExcerpt) {
        this.dmsResponseExcerpt = dmsResponseExcerpt;
    }
}
