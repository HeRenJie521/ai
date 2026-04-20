package com.eaju.ai.service;

import com.eaju.ai.dto.llm.ModeCapabilityDto;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * 根据 AI 应用配置的 modelId，只返回匹配的那一个 provider + 那一个 mode。
     * modelId 可以是 mode 逻辑名（modes_json 的 key），也可以是上游 model ID（value）。
     * 若无匹配，回退到全量列表。
     */
    @Transactional(readOnly = true)
    public List<LlmProviderOptionDto> listOptionsForModelId(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return listEnabledOptions();
        }
        String target = modelId.trim();
        for (LlmProviderConfigEntity e : repository.findAllByEnabledTrueOrderBySortOrderAscCodeAsc()) {
            LlmProviderConfigSnapshot snap;
            try {
                snap = LlmProviderConfigSnapshot.fromEntity(e, objectMapper);
            } catch (JsonProcessingException ex) {
                continue;
            }
            Map<String, String> modes = snap.getModes();
            if (modes == null || modes.isEmpty()) {
                continue;
            }
            // 先按 mode 逻辑名（key）匹配
            if (modes.containsKey(target)) {
                return Collections.singletonList(buildSingleModeDto(e, snap, target));
            }
            // 再按上游 model ID（value）匹配
            for (Map.Entry<String, String> entry : modes.entrySet()) {
                if (target.equals(entry.getValue())) {
                    return Collections.singletonList(buildSingleModeDto(e, snap, entry.getKey()));
                }
            }
        }
        // 未找到匹配，回退全量
        return listEnabledOptions();
    }

    private LlmProviderOptionDto buildSingleModeDto(LlmProviderConfigEntity entity,
                                                     LlmProviderConfigSnapshot snap,
                                                     String modeKey) {
        LlmProviderOptionDto dto = new LlmProviderOptionDto();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setDisplayName(entity.getDisplayName());
        dto.setDefaultMode(modeKey);

        LinkedHashMap<String, String> filteredModes = new LinkedHashMap<>();
        filteredModes.put(modeKey, snap.getModes().get(modeKey));
        dto.setModes(filteredModes);

        Map<String, ModeCapabilityDto> allCaps = snap.buildModeCapabilitiesDto();
        if (allCaps != null && allCaps.containsKey(modeKey)) {
            LinkedHashMap<String, ModeCapabilityDto> filteredCaps = new LinkedHashMap<>();
            filteredCaps.put(modeKey, allCaps.get(modeKey));
            dto.setModeCapabilities(filteredCaps);
        }

        dto.setSupportsThinking(snap.supportsThinkingFlag());
        dto.setDefaultThinkingMode(Boolean.TRUE.equals(snap.defaultsOrEmpty().getThinkingMode()));
        return dto;
    }

    @Transactional(readOnly = true)
    public List<LlmProviderOptionDto> listEnabledOptions() {
        List<LlmProviderOptionDto> out = new ArrayList<LlmProviderOptionDto>();
        for (LlmProviderConfigEntity e : repository.findAllByEnabledTrueOrderBySortOrderAscCodeAsc()) {
            LlmProviderOptionDto dto = new LlmProviderOptionDto();
            dto.setId(e.getId());
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
