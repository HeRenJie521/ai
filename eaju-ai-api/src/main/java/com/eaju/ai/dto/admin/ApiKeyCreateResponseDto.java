package com.eaju.ai.dto.admin;

public class ApiKeyCreateResponseDto extends ApiKeyResponseDto {

    /** 仅在创建时返回完整凭证（API_KEY 或 WEB_EMBED），请立即保存 */
    private String plainSecret;

    public String getPlainSecret() {
        return plainSecret;
    }

    public void setPlainSecret(String plainSecret) {
        this.plainSecret = plainSecret;
    }
}
