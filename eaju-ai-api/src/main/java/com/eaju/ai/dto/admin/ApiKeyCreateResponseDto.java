package com.eaju.ai.dto.admin;

public class ApiKeyCreateResponseDto extends ApiKeyResponseDto {

    /** 仅此响应返回完整密钥，请立即保存 */
    private String plainSecret;

    public String getPlainSecret() {
        return plainSecret;
    }

    public void setPlainSecret(String plainSecret) {
        this.plainSecret = plainSecret;
    }
}
