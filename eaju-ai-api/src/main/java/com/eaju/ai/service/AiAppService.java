package com.eaju.ai.service;

import com.eaju.ai.persistence.entity.AiAppEntity;
import com.eaju.ai.persistence.repository.AiAppRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
                              Long llmModelId) {
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
        e.setLlmModelId(llmModelId);
        return aiAppRepository.save(e);
    }

    @Transactional
    public AiAppEntity update(Long id, String name, String welcomeText, String suggestions,
                              String systemRole, String systemTask, String systemConstraints,
                              Long llmModelId) {
        AiAppEntity e = aiAppRepository.findByIdAndDeletedIsFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("AI 应用不存在"));
        if (StringUtils.hasText(name)) {
            e.setName(name.trim());
        }
        if (welcomeText != null) e.setWelcomeText(welcomeText);
        if (suggestions != null) e.setSuggestions(suggestions);
        if (systemRole != null) e.setSystemRole(systemRole);
        if (systemTask != null) e.setSystemTask(systemTask);
        if (systemConstraints != null) e.setSystemConstraints(systemConstraints);
        // llmModelId: null 表示不修改，0 或负数表示清空
        if (llmModelId != null) {
            e.setLlmModelId(llmModelId > 0 ? llmModelId : null);
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
