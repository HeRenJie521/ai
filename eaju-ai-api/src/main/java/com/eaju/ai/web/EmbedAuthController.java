package com.eaju.ai.web;

import com.eaju.ai.dto.auth.LoginResponseDto;
import com.eaju.ai.dto.embed.AppEmbedLoginRequestDto;
import com.eaju.ai.dto.embed.EmbedLoginRequestDto;
import com.eaju.ai.service.EmbedAuthService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 嵌入网站（WEB_EMBED）免密 SSO 登录接口。
 *
 * <p>此接口不需要 JWT/API Key 鉴权（已在 {@link com.eaju.ai.security.SecurityConfig} 中 permitAll）。
 * 安全性由集成方 HMAC 签名保证。
 */
@RestController
@RequestMapping("/api/embed")
@Validated
public class EmbedAuthController {

    private final EmbedAuthService embedAuthService;

    public EmbedAuthController(EmbedAuthService embedAuthService) {
        this.embedAuthService = embedAuthService;
    }

    /**
     * 嵌入网站静默登录（WEB_EMBED 集成方式）。
     *
     * <p>前端（EmbedView）在加载时携带集成方后端生成的签名参数调用此接口，
     * 验证通过后将 JWT 写入 localStorage，后续页面刷新自动维持登录态。
     */
    @PostMapping("/login")
    public LoginResponseDto embedLogin(@Valid @RequestBody EmbedLoginRequestDto body) {
        return embedAuthService.embedLogin(body);
    }

    /**
     * 应用管理嵌入登录（应用直接嵌入方式）。
     *
     * <p>前端（EmbedView）通过 URL 参数 aid/uid/username 调用此接口，无需 HMAC 签名。
     * 适用于管理员在"应用管理"中配置并生成的嵌入代码。
     */
    @PostMapping("/app-login")
    public LoginResponseDto appEmbedLogin(@Valid @RequestBody AppEmbedLoginRequestDto body) {
        return embedAuthService.appEmbedLogin(body);
    }
}
