package com.eaju.ai.dto.admin;

import javax.validation.constraints.NotBlank;

public class ApiKeyCreateRequestDto {

    @NotBlank
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
