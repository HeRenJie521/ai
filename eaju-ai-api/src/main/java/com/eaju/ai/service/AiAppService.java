package com.eaju.ai.service;

import com.eaju.ai.persistence.entity.AiAppEntity;
import com.eaju.ai.persistence.repository.AiAppRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class AiAppService {

    private final AiAppRepository aiAppRepository;

    public AiAppService(AiAppRepository aiAppRepository) {
        this.aiAppRepository = aiAppRepository;
    }

    @Transactional(readOnly = true)
    public List<AiAppEntity> listAll() {
        return aiAppRepository.findByDeletedIsFalseOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Optional<AiAppEntity> findById(Long id) {
        return aiAppRepository.findByIdAndDeletedIsFalse(id);
    }

    @Transactional
    public AiAppEntity create(String name, String welcomeText, String suggestions,
                              String systemRole, String systemTask, String systemConstraints,
                              String modelId, BigDecimal temperature) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("应用名称不能为空");
        }
        AiAppEntity e = new AiAppEntity();
        e.setName(name.trim());
        e.setWelcomeText(welcomeText);
        e.setSuggestions(suggestions);
        e.setSystemRole(systemRole);
        e.setSystemTask(systemTask);
        e.setSystemConstraints(systemConstraints);
        if (StringUtils.hasText(modelId)) {
            e.setModelId(modelId.trim());
        }
        e.setTemperature(temperature);
        return aiAppRepository.save(e);
    }

    @Transactional
    public AiAppEntity update(Long id, String name, String welcomeText, String suggestions,
                              String systemRole, String systemTask, String systemConstraints,
                              String modelId, BigDecimal temperature) {
        AiAppEntity e = aiAppRepository.findByIdAndDeletedIsFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("AI 应用不存在"));
        if (StringUtils.hasText(name)) {
            e.setName(name.trim());
        }
        // 允许清空（传 ""）或保留（传 null 不更新）
        if (welcomeText != null) {
            e.setWelcomeText(welcomeText);
        }
        if (suggestions != null) {
            e.setSuggestions(suggestions);
        }
        if (systemRole != null) {
            e.setSystemRole(systemRole);
        }
        if (systemTask != null) {
            e.setSystemTask(systemTask);
        }
        if (systemConstraints != null) {
            e.setSystemConstraints(systemConstraints);
        }
        if (modelId != null) {
            e.setModelId(StringUtils.hasText(modelId) ? modelId.trim() : null);
        }
        if (temperature != null) {
            e.setTemperature(temperature);
        }
        return aiAppRepository.save(e);
    }

    @Transactional
    public void delete(Long id) {
        AiAppEntity e = aiAppRepository.findByIdAndDeletedIsFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("AI 应用不存在"));
        e.setDeleted(true);
        aiAppRepository.save(e);
    }
}
