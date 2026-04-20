package com.eaju.ai.service;

import com.eaju.ai.dto.admin.LlmProviderAdminResponseDto;
import com.eaju.ai.dto.admin.LlmProviderCreateRequestDto;
import com.eaju.ai.dto.admin.LlmProviderUpdateRequestDto;
import com.eaju.ai.persistence.entity.LlmProviderConfigEntity;
import com.eaju.ai.persistence.repository.LlmProviderConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class AdminLlmProviderService {

    private final LlmProviderConfigRepository repository;

    public AdminLlmProviderService(LlmProviderConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<LlmProviderAdminResponseDto> listAll() {
        List<LlmProviderAdminResponseDto> out = new ArrayList<>();
        for (LlmProviderConfigEntity e : repository.findAllByOrderBySortOrderAscCodeAsc()) {
            out.add(toResponse(e, true));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public LlmProviderAdminResponseDto getById(Long id) {
        LlmProviderConfigEntity e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在: " + id));
        return toResponse(e, false);
    }

    @Transactional
    public LlmProviderAdminResponseDto create(LlmProviderCreateRequestDto dto) {
        String displayName = dto.getDisplayName().trim();
        String code = allocateUniqueCode(deriveCodeFromDisplayName(displayName), null);
        LlmProviderConfigEntity e = new LlmProviderConfigEntity();
        e.setCode(code);
        e.setDisplayName(displayName);
        e.setApiKey(dto.getApiKey() == null ? "" : dto.getApiKey());
        e.setBaseUrl(dto.getBaseUrl().trim());
        e.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        e.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : nextSortOrder());
        repository.save(e);
        return toResponse(e, true);
    }

    @Transactional
    public LlmProviderAdminResponseDto update(Long id, LlmProviderUpdateRequestDto dto) {
        LlmProviderConfigEntity e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在: " + id));
        if (dto.getDisplayName() != null) {
            String displayName = dto.getDisplayName().trim();
            e.setDisplayName(displayName);
            e.setCode(allocateUniqueCode(deriveCodeFromDisplayName(displayName), e.getId()));
        }
        if (dto.getApiKey() != null) {
            e.setApiKey(dto.getApiKey());
        }
        if (dto.getBaseUrl() != null) {
            e.setBaseUrl(dto.getBaseUrl().trim());
        }
        if (dto.getEnabled() != null) {
            e.setEnabled(dto.getEnabled());
        }
        if (dto.getSortOrder() != null) {
            e.setSortOrder(dto.getSortOrder());
        }
        if (dto.getForceTemperature() != null) {
            e.setForceTemperature(BigDecimal.valueOf(dto.getForceTemperature()));
        }
        if (dto.getThinkingParamStyle() != null) {
            e.setThinkingParamStyle(dto.getThinkingParamStyle().trim());
        }
        if (dto.getJsonModeSystemHint() != null) {
            e.setJsonModeSystemHint(dto.getJsonModeSystemHint());
        }
        if (dto.getStripToolCallIndex() != null) {
            e.setStripToolCallIndex(dto.getStripToolCallIndex());
        }
        repository.save(e);
        return toResponse(e, true);
    }

    @Transactional
    public void delete(Long id) {
        LlmProviderConfigEntity e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在：" + id));
        repository.delete(e);
    }

    private int nextSortOrder() {
        return repository.findAll().stream()
                .mapToInt(LlmProviderConfigEntity::getSortOrder)
                .max()
                .orElse(0) + 10;
    }

    static String deriveCodeFromDisplayName(String displayName) {
        if (!StringUtils.hasText(displayName)) return "LLM";
        String trim = displayName.trim();
        String ascii = trim.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        ascii = ascii.replaceAll("(^_+)|(_+$)", "").replaceAll("_+", "_");
        if (StringUtils.hasText(ascii)) {
            return ascii.length() > 64 ? ascii.substring(0, 64) : ascii;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < trim.length() && sb.length() < 64; i++) {
            char c = trim.charAt(i);
            sb.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        String u = sb.toString().replaceAll("_+", "_").replaceAll("(^_+)|(_+$)", "");
        return StringUtils.hasText(u) ? u : "LLM";
    }

    private String allocateUniqueCode(String base, Long exceptEntityId) {
        String candidate = base.length() > 64 ? base.substring(0, 64) : base;
        for (int n = 2; ; n++) {
            Optional<LlmProviderConfigEntity> existing = repository.findByCodeIgnoreCase(candidate);
            if (!existing.isPresent()) return candidate;
            if (exceptEntityId != null && existing.get().getId().equals(exceptEntityId)) return candidate;
            String suffix = "_" + n;
            String stem = base.length() + suffix.length() > 64
                    ? base.substring(0, Math.max(1, 64 - suffix.length())) : base;
            candidate = stem + suffix;
        }
    }

    private LlmProviderAdminResponseDto toResponse(LlmProviderConfigEntity e, boolean maskKey) {
        LlmProviderAdminResponseDto dto = new LlmProviderAdminResponseDto();
        dto.setId(e.getId());
        dto.setCode(e.getCode());
        dto.setDisplayName(e.getDisplayName());
        dto.setApiKeyMasked(maskApiKey(e.getApiKey()));
        if (!maskKey) {
            dto.setApiKey(e.getApiKey());
        }
        dto.setBaseUrl(e.getBaseUrl());
        dto.setEnabled(e.isEnabled());
        dto.setSortOrder(e.getSortOrder());
        dto.setForceTemperature(e.getForceTemperature() != null ? e.getForceTemperature().doubleValue() : null);
        dto.setThinkingParamStyle(e.getThinkingParamStyle());
        dto.setJsonModeSystemHint(e.isJsonModeSystemHint());
        dto.setStripToolCallIndex(e.isStripToolCallIndex());
        dto.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        dto.setUpdatedAt(e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null);
        return dto;
    }

    private static String maskApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) return "";
        if (apiKey.length() <= 8) return "********";
        return "********" + apiKey.substring(apiKey.length() - 4);
    }
}
