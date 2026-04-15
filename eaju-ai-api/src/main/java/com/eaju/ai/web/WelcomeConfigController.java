package com.eaju.ai.web;

import com.eaju.ai.dto.WelcomeConfigDto;
import com.eaju.ai.persistence.entity.ApiKeyEntity;
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
 * 供嵌入网站获取开场白和推荐问题配置
 */
@RestController
@RequestMapping("/api/chat")
public class WelcomeConfigController {

    private final ApiKeyRepository apiKeyRepository;
    private final ObjectMapper objectMapper;

    public WelcomeConfigController(ApiKeyRepository apiKeyRepository, ObjectMapper objectMapper) {
        this.apiKeyRepository = apiKeyRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取开场引导配置
     * GET /api/chat/welcome?id={integrationId}
     *
     * @param id 集成 ID（api_key.id）
     * @return 开场引导配置
     */
    @GetMapping("/welcome")
    public ResponseEntity<WelcomeConfigDto> getWelcomeConfig(@RequestParam("id") Long id) {
        Optional<ApiKeyEntity> opt = apiKeyRepository.findByIdAndTypeAndEnabledIsTrueAndDeletedIsFalse(id, 2);
        if (!opt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        ApiKeyEntity entity = opt.get();
        WelcomeConfigDto dto = new WelcomeConfigDto();
        dto.setWelcomeText(entity.getWelcomeText());

        // 解析 suggestions JSON
        List<String> suggestions = parseSuggestions(entity.getSuggestions());
        dto.setSuggestions(suggestions);

        return ResponseEntity.ok(dto);
    }

    /**
     * 解析推荐问题 JSON
     */
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
