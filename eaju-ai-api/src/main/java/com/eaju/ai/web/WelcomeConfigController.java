package com.eaju.ai.web;

import com.eaju.ai.dto.WelcomeConfigDto;
import com.eaju.ai.persistence.entity.AiAppEntity;
import com.eaju.ai.persistence.repository.AiAppRepository;
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

    private final AiAppRepository aiAppRepository;
    private final ObjectMapper objectMapper;

    public WelcomeConfigController(AiAppRepository aiAppRepository,
                                   ObjectMapper objectMapper) {
        this.aiAppRepository = aiAppRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取开场引导配置（应用管理嵌入方式）
     * GET /api/chat/welcome-app?aid={appId}
     *
     * @param aid AI 应用 ID（ai_app.id）
     */
    @GetMapping("/welcome-app")
    public ResponseEntity<WelcomeConfigDto> getWelcomeConfigByApp(@RequestParam("aid") Long aid) {
        Optional<AiAppEntity> appOpt = aiAppRepository.findByIdAndDeletedIsFalse(aid);
        if (!appOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(buildDto(appOpt.get()));
    }

    private WelcomeConfigDto buildDto(AiAppEntity app) {
        WelcomeConfigDto dto = new WelcomeConfigDto();
        dto.setWelcomeText(app.getWelcomeText());
        dto.setSuggestions(parseSuggestions(app.getSuggestions()));
        dto.setModelId(app.getModelId());
        return dto;
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
