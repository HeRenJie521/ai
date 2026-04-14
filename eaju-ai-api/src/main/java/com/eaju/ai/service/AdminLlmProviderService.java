package com.eaju.ai.service;

import com.eaju.ai.dto.admin.LlmProviderAdminResponseDto;
import com.eaju.ai.dto.admin.LlmProviderCreateRequestDto;
import com.eaju.ai.dto.admin.LlmProviderUpdateRequestDto;
import com.eaju.ai.persistence.entity.LlmProviderConfigEntity;
import com.eaju.ai.persistence.repository.LlmProviderConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class AdminLlmProviderService {

    private static final String DEFAULT_MODE_PLACEHOLDER = "default";
    private static final String DEFAULT_MODES_JSON = "{\"default\":\"default\"}";

    private final LlmProviderConfigRepository repository;
    private final ObjectMapper objectMapper;

    public AdminLlmProviderService(LlmProviderConfigRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<LlmProviderAdminResponseDto> listAll() {
        List<LlmProviderAdminResponseDto> out = new ArrayList<LlmProviderAdminResponseDto>();
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
        validateJson(DEFAULT_MODES_JSON, "modesJson");
        LlmProviderConfigEntity e = new LlmProviderConfigEntity();
        e.setCode(code);
        e.setDisplayName(displayName);
        e.setApiKey(dto.getApiKey() == null ? "" : dto.getApiKey());
        e.setBaseUrl(dto.getBaseUrl().trim());
        e.setDefaultMode(DEFAULT_MODE_PLACEHOLDER);
        e.setModesJson(DEFAULT_MODES_JSON);
        e.setInferenceDefaultsJson(null);
        e.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        e.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : nextSortOrder());
        validateDefaultModeInModes(e.getDefaultMode(), e.getModesJson());
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
        if (dto.getDefaultMode() != null) {
            e.setDefaultMode(dto.getDefaultMode().trim());
        }
        if (dto.getModesJson() != null) {
            validateJson(dto.getModesJson(), "modesJson");
            e.setModesJson(dto.getModesJson().trim());
        }
        if (dto.getInferenceDefaultsJson() != null) {
            if (StringUtils.hasText(dto.getInferenceDefaultsJson())) {
                validateJson(dto.getInferenceDefaultsJson(), "inferenceDefaultsJson");
            }
            e.setInferenceDefaultsJson(trimToNull(dto.getInferenceDefaultsJson()));
        }
        if (dto.getEnabled() != null) {
            e.setEnabled(dto.getEnabled());
        }
        if (dto.getSortOrder() != null) {
            e.setSortOrder(dto.getSortOrder());
        }
        validateDefaultModeInModes(e.getDefaultMode(), e.getModesJson());
        repository.save(e);
        return toResponse(e, true);
    }

    private int nextSortOrder() {
        return repository.findAll().stream()
                .mapToInt(LlmProviderConfigEntity::getSortOrder)
                .max()
                .orElse(0) + 10;
    }

    /**
     * 由显示名称生成稳定、可作为 {@code provider} 的 code（库唯一）；ASCII 优先，否则保留字母数字（含中文等）并截断。
     */
    static String deriveCodeFromDisplayName(String displayName) {
        if (!StringUtils.hasText(displayName)) {
            return "LLM";
        }
        String trim = displayName.trim();
        String ascii = trim.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        ascii = ascii.replaceAll("(^_+)|(_+$)", "").replaceAll("_+", "_");
        if (StringUtils.hasText(ascii)) {
            return ascii.length() > 64 ? ascii.substring(0, 64) : ascii;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < trim.length() && sb.length() < 64; i++) {
            char c = trim.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String u = sb.toString().replaceAll("_+", "_").replaceAll("(^_+)|(_+$)", "");
        return StringUtils.hasText(u) ? u : "LLM";
    }

    private String allocateUniqueCode(String base, Long exceptEntityId) {
        String candidate = base.length() > 64 ? base.substring(0, 64) : base;
        for (int n = 2; ; n++) {
            Optional<LlmProviderConfigEntity> existing = repository.findByCodeIgnoreCase(candidate);
            if (!existing.isPresent()) {
                return candidate;
            }
            if (exceptEntityId != null && existing.get().getId().equals(exceptEntityId)) {
                return candidate;
            }
            String suffix = "_" + n;
            String stem = base.length() + suffix.length() > 64 ? base.substring(0, Math.max(1, 64 - suffix.length())) : base;
            candidate = stem + suffix;
        }
    }

    private void validateJson(String raw, String field) {
        try {
            objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new IllegalArgumentException(field + " 不是合法 JSON: " + ex.getMessage());
        }
    }

    private void validateDefaultModeInModes(String defaultMode, String modesJsonRaw) {
        if (!StringUtils.hasText(defaultMode)) {
            throw new IllegalArgumentException("defaultMode 不能为空，请在「高级配置」中设置");
        }
        if (!StringUtils.hasText(modesJsonRaw)) {
            throw new IllegalArgumentException("modesJson 不能为空");
        }
        try {
            JsonNode root = objectMapper.readTree(modesJsonRaw);
            if (!root.isObject()) {
                throw new IllegalArgumentException("modesJson 必须是 JSON 对象");
            }
            String dm = defaultMode.trim();
            if (!root.has(dm)) {
                throw new IllegalArgumentException("defaultMode「" + dm + "」必须在 modesJson 的键中存在");
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("modesJson 校验失败: " + ex.getMessage());
        }
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
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
        dto.setDefaultMode(e.getDefaultMode());
        dto.setModesJson(e.getModesJson());
        dto.setInferenceDefaultsJson(e.getInferenceDefaultsJson());
        dto.setEnabled(e.isEnabled());
        dto.setSortOrder(e.getSortOrder());
        dto.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        dto.setUpdatedAt(e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null);
        return dto;
    }

    private static String maskApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "********";
        }
        return "********" + apiKey.substring(apiKey.length() - 4);
    }
}
