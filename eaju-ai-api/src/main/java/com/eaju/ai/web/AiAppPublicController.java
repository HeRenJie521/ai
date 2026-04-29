package com.eaju.ai.web;

import com.eaju.ai.dto.UserAiAppDto;
import com.eaju.ai.persistence.entity.AiAppEntity;
import com.eaju.ai.persistence.entity.LlmModelEntity;
import com.eaju.ai.persistence.entity.LlmProviderConfigEntity;
import com.eaju.ai.persistence.repository.LlmModelRepository;
import com.eaju.ai.persistence.repository.LlmProviderConfigRepository;
import com.eaju.ai.service.AiAppService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/ai-apps")
public class AiAppPublicController {

    private final AiAppService aiAppService;
    private final LlmModelRepository llmModelRepository;
    private final LlmProviderConfigRepository llmProviderRepository;

    public AiAppPublicController(AiAppService aiAppService,
                                 LlmModelRepository llmModelRepository,
                                 LlmProviderConfigRepository llmProviderRepository) {
        this.aiAppService = aiAppService;
        this.llmModelRepository = llmModelRepository;
        this.llmProviderRepository = llmProviderRepository;
    }

    @GetMapping
    public List<UserAiAppDto> list() {
        List<UserAiAppDto> out = new ArrayList<>();
        for (AiAppEntity e : aiAppService.listAll()) {
            out.add(toDto(e));
        }
        return out;
    }

    private UserAiAppDto toDto(AiAppEntity e) {
        UserAiAppDto dto = new UserAiAppDto();
        dto.setId(e.getId());
        dto.setName(e.getName());
        if (e.getLlmModelId() != null) {
            LlmModelEntity model = llmModelRepository.findById(e.getLlmModelId()).orElse(null);
            if (model != null) {
                LlmProviderConfigEntity provider = llmProviderRepository.findById(model.getProviderId()).orElse(null);
                String providerName = provider != null ? provider.getDisplayName() : "";
                dto.setModelDisplayName(providerName + "·" + model.getName());
                dto.setProviderCode(provider != null ? provider.getCode() : null);
                dto.setModeKey(model.getName());
                dto.setDeepThinking(model.isDeepThinking());
                dto.setVision(model.isVision());
                dto.setStreamOutput(model.isStreamOutput());
            }
        }
        return dto;
    }
}
