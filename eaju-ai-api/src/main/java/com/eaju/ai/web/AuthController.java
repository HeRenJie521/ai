package com.eaju.ai.web;

import com.eaju.ai.dto.auth.LoginRequestDto;
import com.eaju.ai.dto.auth.LoginResponseDto;
import com.eaju.ai.security.AuthUserPrincipal;
import com.eaju.ai.service.AuthService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponseDto login(@Valid @RequestBody LoginRequestDto request) {
        return authService.login(request);
    }

    /**
     * 登出：删除 Redis 中与本 JWT {@code jti} 绑定的登录会话（前端仍应清除本地缓存）。
     */
    @PostMapping("/logout")
    public void logout(@AuthenticationPrincipal AuthUserPrincipal principal) {
        if (principal != null) {
            authService.logout(principal.getJti());
        }
    }
}
