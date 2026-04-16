package com.eaju.ai.web;

import com.eaju.ai.dto.llm.LlmProviderOptionDto;
import com.eaju.ai.persistence.entity.AiAppEntity;
import com.eaju.ai.security.CallerPrincipal;
import com.eaju.ai.service.AiAppService;
import com.eaju.ai.service.LlmProviderConfigService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.List;

@RestController
@RequestMapping("/api/llm-providers")
public class LlmProviderPublicController {

    private final LlmProviderConfigService llmProviderConfigService;
    private final AiAppService aiAppService;

    public LlmProviderPublicController(LlmProviderConfigService llmProviderConfigService,
                                       AiAppService aiAppService) {
        this.llmProviderConfigService = llmProviderConfigService;
        this.aiAppService = aiAppService;
    }

    @GetMapping
    public List<LlmProviderOptionDto> listEnabled(Authentication authentication) {
        Long appId = CallerPrincipal.appId(authentication);
        if (appId != null) {
            // 应用嵌入登录：只返回该应用配置的模型，不暴露全量模型列表
            String modelId = aiAppService.findById(appId)
                    .map(AiAppEntity::getModelId)
                    .filter(StringUtils::hasText)
                    .orElse(null);
            return llmProviderConfigService.listOptionsForModelId(modelId);
        }
        return llmProviderConfigService.listEnabledOptions();
    }
}
