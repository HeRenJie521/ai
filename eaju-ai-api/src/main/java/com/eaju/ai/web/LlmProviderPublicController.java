package com.eaju.ai.web;

import com.eaju.ai.dto.llm.LlmProviderOptionDto;
import com.eaju.ai.service.LlmProviderConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/llm-providers")
public class LlmProviderPublicController {

    private final LlmProviderConfigService llmProviderConfigService;

    public LlmProviderPublicController(LlmProviderConfigService llmProviderConfigService) {
        this.llmProviderConfigService = llmProviderConfigService;
    }

    @GetMapping
    public List<LlmProviderOptionDto> listEnabled() {
        return llmProviderConfigService.listEnabledOptions();
    }
}
