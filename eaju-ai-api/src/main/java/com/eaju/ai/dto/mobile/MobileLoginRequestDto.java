package com.eaju.ai.dto.mobile;

import javax.validation.constraints.NotBlank;

public class MobileLoginRequestDto {

    @NotBlank(message = "手机号不能为空")
    private String phone;

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
