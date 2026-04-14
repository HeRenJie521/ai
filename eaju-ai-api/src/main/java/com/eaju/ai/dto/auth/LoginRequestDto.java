package com.eaju.ai.dto.auth;

import javax.validation.constraints.NotBlank;

public class LoginRequestDto {

    @NotBlank
    private String phone;

    /** 明文；将按 DMS 规则做 MD5 后取 [8,24) 再提交；允许空串（与 type=1 等场景一致） */
    private String password;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
