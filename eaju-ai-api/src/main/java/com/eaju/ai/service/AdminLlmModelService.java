package com.eaju.ai.service;

import com.eaju.ai.dto.admin.LlmModelAdminResponseDto;
import com.eaju.ai.dto.admin.LlmModelSaveRequestDto;
import com.eaju.ai.persistence.entity.LlmModelEntity;
import com.eaju.ai.persistence.entity.LlmProviderConfigEntity;
import com.eaju.ai.persistence.repository.LlmModelRepository;
import com.eaju.ai.persistence.repository.LlmProviderConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminLlmModelService {

    private final LlmModelRepository modelRepository;
    private final LlmProviderConfigRepository providerRepository;

    public AdminLlmModelService(LlmModelRepository modelRepository,
                                 LlmProviderConfigRepository providerRepository) {
        this.modelRepository = modelRepository;
        this.providerRepository = providerRepository;
    }

    @Transactional(readOnly = true)
    public List<LlmModelAdminResponseDto> listAll() {
        List<LlmModelAdminResponseDto> out = new ArrayList<>();
        for (LlmModelEntity m : modelRepository.findAll()) {
            LlmProviderConfigEntity p = providerRepository.findById(m.getProviderId()).orElse(null);
            out.add(toDto(m, p));
        }
        // 按提供商 sort_order, 然后按模型 sort_order 排序
        out.sort((a, b) -> {
            int cmp = Long.compare(a.getProviderId(), b.getProviderId());
            if (cmp != 0) return cmp;
            return Integer.compare(a.getSortOrder(), b.getSortOrder());
        });
        return out;
    }

    @Transactional(readOnly = true)
    public List<LlmModelAdminResponseDto> listByProvider(Long providerId) {
        List<LlmModelAdminResponseDto> out = new ArrayList<>();
        LlmProviderConfigEntity p = providerRepository.findById(providerId).orElse(null);
        for (LlmModelEntity m : modelRepository.findByProviderIdOrderBySortOrderAscNameAsc(providerId)) {
            out.add(toDto(m, p));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public LlmModelAdminResponseDto getById(Long id) {
        LlmModelEntity m = modelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模型不存在: " + id));
        LlmProviderConfigEntity p = providerRepository.findById(m.getProviderId()).orElse(null);
        return toDto(m, p);
    }

    @Transactional
    public LlmModelAdminResponseDto create(LlmModelSaveRequestDto dto) {
        if (!StringUtils.hasText(dto.getName())) {
            throw new IllegalArgumentException("模型名称不能为空");
        }
        LlmProviderConfigEntity provider = providerRepository.findById(dto.getProviderId())
                .orElseThrow(() -> new IllegalArgumentException("模型提供商不存在: " + dto.getProviderId()));

        LlmModelEntity m = new LlmModelEntity();
        m.setProviderId(provider.getId());
        applyDto(m, dto);
        modelRepository.save(m);
        return toDto(m, provider);
    }

    @Transactional
    public LlmModelAdminResponseDto update(Long id, LlmModelSaveRequestDto dto) {
        LlmModelEntity m = modelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模型不存在: " + id));
        if (dto.getProviderId() != null) {
            providerRepository.findById(dto.getProviderId())
                    .orElseThrow(() -> new IllegalArgumentException("模型提供商不存在: " + dto.getProviderId()));
            m.setProviderId(dto.getProviderId());
        }
        applyDto(m, dto);
        modelRepository.save(m);
        LlmProviderConfigEntity p = providerRepository.findById(m.getProviderId()).orElse(null);
        return toDto(m, p);
    }

    @Transactional
    public void delete(Long id) {
        LlmModelEntity m = modelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模型不存在: " + id));
        modelRepository.delete(m);
    }

    private static void applyDto(LlmModelEntity m, LlmModelSaveRequestDto dto) {
        String name = dto.getName().trim();
        m.setName(name);
        String upstreamId = StringUtils.hasText(dto.getUpstreamModelId())
                ? dto.getUpstreamModelId().trim() : name;
        m.setUpstreamModelId(upstreamId);

        if (dto.getTextGeneration() != null)       m.setTextGeneration(dto.getTextGeneration());
        if (dto.getDeepThinking() != null)          m.setDeepThinking(dto.getDeepThinking());
        if (dto.getVision() != null)                m.setVision(dto.getVision());
        if (dto.getStreamOutput() != null)          m.setStreamOutput(dto.getStreamOutput());
        if (dto.getToolCall() != null)              m.setToolCall(dto.getToolCall());
        if (dto.getForceThinkingEnabled() != null)  m.setForceThinkingEnabled(dto.getForceThinkingEnabled());

        // 推理参数（null 表示清空）
        m.setTemperature(dto.getTemperature() != null ? BigDecimal.valueOf(dto.getTemperature()) : null);
        m.setMaxTokens(dto.getMaxTokens());
        m.setTopP(dto.getTopP() != null ? BigDecimal.valueOf(dto.getTopP()) : null);
        m.setTopK(dto.getTopK());
        m.setFrequencyPenalty(dto.getFrequencyPenalty() != null ? BigDecimal.valueOf(dto.getFrequencyPenalty()) : null);
        m.setPresencePenalty(dto.getPresencePenalty() != null ? BigDecimal.valueOf(dto.getPresencePenalty()) : null);
        m.setResponseFormat(StringUtils.hasText(dto.getResponseFormat()) ? dto.getResponseFormat().trim() : null);
        m.setThinkingMode(dto.getThinkingMode());
        m.setContextWindow(dto.getContextWindow());

        if (dto.getSortOrder() != null) m.setSortOrder(dto.getSortOrder());
        if (dto.getEnabled() != null)   m.setEnabled(dto.getEnabled());
    }

    public static LlmModelAdminResponseDto toDto(LlmModelEntity m, LlmProviderConfigEntity p) {
        LlmModelAdminResponseDto dto = new LlmModelAdminResponseDto();
        dto.setId(m.getId());
        dto.setProviderId(m.getProviderId());
        if (p != null) {
            dto.setProviderDisplayName(p.getDisplayName());
            dto.setProviderCode(p.getCode());
        }
        dto.setName(m.getName());
        dto.setUpstreamModelId(m.getUpstreamModelId());
        dto.setTextGeneration(m.isTextGeneration());
        dto.setDeepThinking(m.isDeepThinking());
        dto.setVision(m.isVision());
        dto.setStreamOutput(m.isStreamOutput());
        dto.setToolCall(m.isToolCall());
        dto.setForceThinkingEnabled(m.isForceThinkingEnabled());
        dto.setTemperature(m.getTemperature() != null ? m.getTemperature().doubleValue() : null);
        dto.setMaxTokens(m.getMaxTokens());
        dto.setTopP(m.getTopP() != null ? m.getTopP().doubleValue() : null);
        dto.setTopK(m.getTopK());
        dto.setFrequencyPenalty(m.getFrequencyPenalty() != null ? m.getFrequencyPenalty().doubleValue() : null);
        dto.setPresencePenalty(m.getPresencePenalty() != null ? m.getPresencePenalty().doubleValue() : null);
        dto.setResponseFormat(m.getResponseFormat());
        dto.setThinkingMode(m.getThinkingMode());
        dto.setContextWindow(m.getContextWindow());
        dto.setSortOrder(m.getSortOrder());
        dto.setEnabled(m.isEnabled());
        dto.setCreatedAt(m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
        dto.setUpdatedAt(m.getUpdatedAt() != null ? m.getUpdatedAt().toString() : null);
        return dto;
    }
}
