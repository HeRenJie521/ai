package com.eaju.ai.service;

import com.eaju.ai.llm.LlmProviderConfigSnapshot;
import com.eaju.ai.persistence.entity.LlmProviderConfigEntity;
import com.eaju.ai.persistence.repository.LlmProviderConfigRepository;
import com.eaju.ai.dto.llm.LlmProviderOptionDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Service
public class LlmProviderConfigService {

    private final LlmProviderConfigRepository repository;
    private final ObjectMapper objectMapper;

    public LlmProviderConfigService(LlmProviderConfigRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * 按请求中的 provider 字符串加载启用中的配置；未知或已禁用则抛出 {@link IllegalArgumentException}。
     */
    public LlmProviderConfigSnapshot requireSnapshot(String providerCode) {
        if (!StringUtils.hasText(providerCode)) {
            throw new IllegalArgumentException("provider 不能为空");
        }
        String key = providerCode.trim();
        LlmProviderConfigEntity entity = repository.findByCodeIgnoreCase(key)
                .orElseThrow(() -> new IllegalArgumentException("未知 provider: " + key));
        if (!entity.isEnabled()) {
            throw new IllegalArgumentException("provider 已禁用: " + key);
        }
        try {
            return LlmProviderConfigSnapshot.fromEntity(entity, objectMapper);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("解析提供方配置 JSON 失败: " + key, ex);
        }
    }

    @Transactional(readOnly = true)
    public List<LlmProviderOptionDto> listEnabledOptions() {
        List<LlmProviderOptionDto> out = new ArrayList<LlmProviderOptionDto>();
        for (LlmProviderConfigEntity e : repository.findAllByEnabledTrueOrderBySortOrderAscCodeAsc()) {
            LlmProviderOptionDto dto = new LlmProviderOptionDto();
            dto.setCode(e.getCode());
            dto.setDisplayName(e.getDisplayName());
            dto.setDefaultMode(e.getDefaultMode());
            try {
                LlmProviderConfigSnapshot snap = LlmProviderConfigSnapshot.fromEntity(e, objectMapper);
                dto.setModes(new LinkedHashMap<String, String>(snap.getModes()));
                dto.setModeCapabilities(snap.buildModeCapabilitiesDto());
                dto.setSupportsThinking(snap.supportsThinkingFlag());
                dto.setDefaultThinkingMode(Boolean.TRUE.equals(snap.defaultsOrEmpty().getThinkingMode()));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("解析提供方 modes_json 失败: " + e.getCode(), ex);
            }
            out.add(dto);
        }
        return out;
    }
}
