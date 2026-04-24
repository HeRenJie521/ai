package com.eaju.ai.web;

import com.eaju.ai.dto.auth.LoginResponseDto;
import com.eaju.ai.dto.mobile.MobileAiAppDto;
import com.eaju.ai.dto.mobile.MobileLoginRequestDto;
import com.eaju.ai.persistence.entity.AiAppEntity;
import com.eaju.ai.persistence.entity.LlmModelEntity;
import com.eaju.ai.persistence.entity.LlmProviderConfigEntity;
import com.eaju.ai.persistence.repository.AiAppRepository;
import com.eaju.ai.persistence.repository.LlmModelRepository;
import com.eaju.ai.persistence.repository.LlmProviderConfigRepository;
import com.eaju.ai.service.MobileAuthService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

/**
 * 移动端接口：免密登录与应用列表。
 * /api/mobile/login 无需认证；/api/mobile/apps 需要 JWT。
 */
@RestController
@RequestMapping("/api/mobile")
@Validated
public class MobileController {

    private final MobileAuthService mobileAuthService;
    private final AiAppRepository aiAppRepository;
    private final LlmModelRepository llmModelRepository;
    private final LlmProviderConfigRepository llmProviderRepository;

    public MobileController(MobileAuthService mobileAuthService,
                            AiAppRepository aiAppRepository,
                            LlmModelRepository llmModelRepository,
                            LlmProviderConfigRepository llmProviderRepository) {
        this.mobileAuthService = mobileAuthService;
        this.aiAppRepository = aiAppRepository;
        this.llmModelRepository = llmModelRepository;
        this.llmProviderRepository = llmProviderRepository;
    }

    /**
     * 移动端免密登录：传入手机号，通过 DMS 免密接口验证后签发 JWT。
     */
    @PostMapping("/login")
    public LoginResponseDto mobileLogin(@Valid @RequestBody MobileLoginRequestDto body) {
        return mobileAuthService.mobileLogin(body.getPhone());
    }

    /**
     * 获取所有 AI 应用列表（移动端应用中心）。需要 JWT 认证。
     */
    @GetMapping("/apps")
    public List<MobileAiAppDto> listApps() {
        List<MobileAiAppDto> out = new ArrayList<>();
        for (AiAppEntity e : aiAppRepository.findByDeletedIsFalseOrderByCreatedAtDesc()) {
            out.add(toDto(e));
        }
        return out;
    }

    private MobileAiAppDto toDto(AiAppEntity e) {
        MobileAiAppDto dto = new MobileAiAppDto();
        dto.setId(e.getId());
        dto.setName(e.getName());
        dto.setWelcomeText(e.getWelcomeText());
        dto.setSuggestions(e.getSuggestions());
        dto.setModelDisplayName(resolveModelDisplayName(e.getLlmModelId()));
        return dto;
    }

    private String resolveModelDisplayName(Long llmModelId) {
        if (llmModelId == null) return null;
        LlmModelEntity model = llmModelRepository.findById(llmModelId).orElse(null);
        if (model == null) return null;
        LlmProviderConfigEntity provider = llmProviderRepository.findById(model.getProviderId()).orElse(null);
        String providerName = provider != null ? provider.getDisplayName() : "";
        return providerName + "·" + model.getName();
    }
}
