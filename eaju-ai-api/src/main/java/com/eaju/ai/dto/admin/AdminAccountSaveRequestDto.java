package com.eaju.ai.dto.admin;

/**
 * 系统管理员账号保存请求 DTO
 */
public class AdminAccountSaveRequestDto {

    private String phone;
    private String name;
    private boolean enabled;

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
