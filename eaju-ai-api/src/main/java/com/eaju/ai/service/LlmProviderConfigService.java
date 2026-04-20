package com.eaju.ai.service;

import com.eaju.ai.dto.llm.ModeCapabilityDto;
import com.eaju.ai.llm.LlmProviderConfigSnapshot;
import com.eaju.ai.persistence.entity.LlmModelEntity;
import com.eaju.ai.persistence.entity.LlmProviderConfigEntity;
import com.eaju.ai.persistence.repository.LlmModelRepository;
import com.eaju.ai.persistence.repository.LlmProviderConfigRepository;
import com.eaju.ai.dto.llm.LlmProviderOptionDto;
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
    private final LlmModelRepository modelRepository;

    public LlmProviderConfigService(LlmProviderConfigRepository repository,
                                     LlmModelRepository modelRepository) {
        this.repository = repository;
        this.modelRepository = modelRepository;
    }

    /**
     * 按 provider code 加载启用中的配置快照；未知或已禁用则抛出异常。
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
        List<LlmModelEntity> models = modelRepository
                .findByProviderIdAndEnabledTrueOrderBySortOrderAscNameAsc(entity.getId());
        return LlmProviderConfigSnapshot.fromEntityAndModels(entity, models);
    }

    /**
     * 根据 llm_model.id 直接返回对应的单模型 provider option；找不到则回退全量列表。
     */
    @Transactional(readOnly = true)
    public List<LlmProviderOptionDto> listOptionsForLlmModelId(Long modelId) {
        if (modelId == null) return listEnabledOptions();
        LlmModelEntity model = modelRepository.findById(modelId).orElse(null);
        if (model == null || !model.isEnabled()) return listEnabledOptions();
        LlmProviderConfigEntity provider = repository.findById(model.getProviderId()).orElse(null);
        if (provider == null || !provider.isEnabled()) return listEnabledOptions();
        List<LlmModelEntity> models = modelRepository
                .findByProviderIdAndEnabledTrueOrderBySortOrderAscNameAsc(provider.getId());
        LlmProviderConfigSnapshot snap = LlmProviderConfigSnapshot.fromEntityAndModels(provider, models);
        return Collections.singletonList(buildSingleModeDto(provider, snap, model.getName()));
    }

    /**
     * 兼容旧接口：根据逻辑 mode key 返回匹配的单模型 provider option；找不到则回退全量列表。
     */
    @Transactional(readOnly = true)
    public List<LlmProviderOptionDto> listOptionsForModelId(String modelId) {
        if (!StringUtils.hasText(modelId)) return listEnabledOptions();
        String target = modelId.trim();
        for (LlmProviderConfigEntity e : repository.findAllByEnabledTrueOrderBySortOrderAscCodeAsc()) {
            List<LlmModelEntity> models = modelRepository
                    .findByProviderIdAndEnabledTrueOrderBySortOrderAscNameAsc(e.getId());
            LlmProviderConfigSnapshot snap = LlmProviderConfigSnapshot.fromEntityAndModels(e, models);
            Map<String, String> modes = snap.getModes();
            if (modes == null || modes.isEmpty()) continue;
            if (modes.containsKey(target)) {
                return Collections.singletonList(buildSingleModeDto(e, snap, target));
            }
            for (Map.Entry<String, String> entry : modes.entrySet()) {
                if (target.equals(entry.getValue())) {
                    return Collections.singletonList(buildSingleModeDto(e, snap, entry.getKey()));
                }
            }
        }
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
        if (snap.getModes().containsKey(modeKey)) {
            filteredModes.put(modeKey, snap.getModes().get(modeKey));
        }
        dto.setModes(filteredModes);

        Map<String, ModeCapabilityDto> allCaps = snap.buildModeCapabilitiesDto();
        if (allCaps != null && allCaps.containsKey(modeKey)) {
            LinkedHashMap<String, ModeCapabilityDto> filteredCaps = new LinkedHashMap<>();
            filteredCaps.put(modeKey, allCaps.get(modeKey));
            dto.setModeCapabilities(filteredCaps);
        }

        dto.setSupportsThinking(snap.modeSupportsThinkingApi(modeKey));
        dto.setDefaultThinkingMode(Boolean.TRUE.equals(snap.getModeDefaults(modeKey).getThinkingMode()));
        return dto;
    }

    @Transactional(readOnly = true)
    public List<LlmProviderOptionDto> listEnabledOptions() {
        List<LlmProviderOptionDto> out = new ArrayList<>();
        for (LlmProviderConfigEntity e : repository.findAllByEnabledTrueOrderBySortOrderAscCodeAsc()) {
            List<LlmModelEntity> models = modelRepository
                    .findByProviderIdAndEnabledTrueOrderBySortOrderAscNameAsc(e.getId());
            LlmProviderConfigSnapshot snap = LlmProviderConfigSnapshot.fromEntityAndModels(e, models);
            LlmProviderOptionDto dto = new LlmProviderOptionDto();
            dto.setId(e.getId());
            dto.setCode(e.getCode());
            dto.setDisplayName(e.getDisplayName());
            dto.setDefaultMode(snap.getDefaultMode());
            dto.setModes(new LinkedHashMap<>(snap.getModes()));
            dto.setModeCapabilities(snap.buildModeCapabilitiesDto());
            // 是否有任意模型支持 thinking（深度思考即支持）
            boolean supportsThinking = models.stream().anyMatch(LlmModelEntity::isDeepThinking);
            dto.setSupportsThinking(supportsThinking);
            dto.setDefaultThinkingMode(Boolean.TRUE.equals(snap.getModeDefaults(snap.getDefaultMode()).getThinkingMode()));
            out.add(dto);
        }
        return out;
    }
}
