package com.eaju.ai.web;

import com.eaju.ai.dto.WelcomeConfigDto;
import com.eaju.ai.persistence.entity.AiAppEntity;
import com.eaju.ai.persistence.entity.ApiKeyEntity;
import com.eaju.ai.persistence.repository.AiAppRepository;
import com.eaju.ai.persistence.repository.ApiKeyRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 开场引导配置接口
 * 供嵌入网站获取开场白、推荐问题和默认模型配置（从关联的 AI 应用加载）
 */
@RestController
@RequestMapping("/api/chat")
public class WelcomeConfigController {

    private final ApiKeyRepository apiKeyRepository;
    private final AiAppRepository aiAppRepository;
    private final ObjectMapper objectMapper;

    public WelcomeConfigController(ApiKeyRepository apiKeyRepository,
                                    AiAppRepository aiAppRepository,
                                    ObjectMapper objectMapper) {
        this.apiKeyRepository = apiKeyRepository;
        this.aiAppRepository = aiAppRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取开场引导配置
     * GET /api/chat/welcome?id={integrationId}
     *
     * @param id 集成 ID（api_key.id，type=2）
     */
    @GetMapping("/welcome")
    public ResponseEntity<WelcomeConfigDto> getWelcomeConfig(@RequestParam("id") Long id) {
        Optional<ApiKeyEntity> integrationOpt =
                apiKeyRepository.findByIdAndTypeAndEnabledIsTrueAndDeletedIsFalse(id, 2);
        if (!integrationOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        ApiKeyEntity integration = integrationOpt.get();
        if (integration.getAppId() == null) {
            // 集成尚未关联 AI 应用，返回空配置
            return ResponseEntity.ok(new WelcomeConfigDto());
        }

        Optional<AiAppEntity> appOpt = aiAppRepository.findByIdAndDeletedIsFalse(integration.getAppId());
        if (!appOpt.isPresent()) {
            return ResponseEntity.ok(new WelcomeConfigDto());
        }

        AiAppEntity app = appOpt.get();
        WelcomeConfigDto dto = new WelcomeConfigDto();
        dto.setWelcomeText(app.getWelcomeText());
        dto.setSuggestions(parseSuggestions(app.getSuggestions()));
        dto.setModelId(app.getModelId());

        return ResponseEntity.ok(dto);
    }

    private List<String> parseSuggestions(String suggestionsJson) {
        if (suggestionsJson == null || suggestionsJson.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<String> list = objectMapper.readValue(suggestionsJson, new TypeReference<List<String>>() {});
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
