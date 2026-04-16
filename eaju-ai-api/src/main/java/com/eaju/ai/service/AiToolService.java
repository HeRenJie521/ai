package com.eaju.ai.service;

import com.eaju.ai.persistence.entity.AiAppToolEntity;
import com.eaju.ai.persistence.entity.AiToolEntity;
import com.eaju.ai.persistence.repository.AiAppToolRepository;
import com.eaju.ai.persistence.repository.AiToolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class AiToolService {

    private final AiToolRepository aiToolRepository;
    private final AiAppToolRepository aiAppToolRepository;

    public AiToolService(AiToolRepository aiToolRepository, AiAppToolRepository aiAppToolRepository) {
        this.aiToolRepository = aiToolRepository;
        this.aiAppToolRepository = aiAppToolRepository;
    }

    @Transactional(readOnly = true)
    public List<AiToolEntity> listAll() {
        return aiToolRepository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public List<AiToolEntity> findEnabledToolsByAppId(Long appId) {
        if (appId == null) return new ArrayList<>();
        return aiToolRepository.findEnabledToolsByAppId(appId);
    }

    @Transactional(readOnly = true)
    public List<Long> findBoundToolIdsByAppId(Long appId) {
        List<Long> ids = new ArrayList<>();
        for (AiAppToolEntity e : aiAppToolRepository.findByAppIdOrderBySortOrderAscIdAsc(appId)) {
            ids.add(e.getToolId());
        }
        return ids;
    }

    @Transactional
    public AiToolEntity create(String name, String label, String description,
                               String httpMethod, String url, String headersJson,
                               String bodyTemplate, String contentType,
                               String methodName, String dataParamsJson,
                               String responseParamsJson, String paramsSchemaJson) {
        validate(name, description, url, paramsSchemaJson);
        AiToolEntity e = new AiToolEntity();
        e.setName(name.trim());
        e.setLabel(StringUtils.hasText(label) ? label.trim() : name.trim());
        e.setDescription(description.trim());
        e.setHttpMethod(StringUtils.hasText(httpMethod) ? httpMethod.trim().toUpperCase() : "POST");
        e.setUrl(url.trim());
        e.setHeadersJson(StringUtils.hasText(headersJson) ? headersJson.trim() : null);
        e.setBodyTemplate(StringUtils.hasText(bodyTemplate) ? bodyTemplate.trim() : null);
        e.setContentType(StringUtils.hasText(contentType) ? contentType.trim() : "application/json");
        e.setMethodName(StringUtils.hasText(methodName) ? methodName.trim() : null);
        e.setDataParamsJson(StringUtils.hasText(dataParamsJson) ? dataParamsJson.trim() : null);
        e.setResponseParamsJson(StringUtils.hasText(responseParamsJson) ? responseParamsJson.trim() : null);
        e.setParamsSchemaJson(paramsSchemaJson.trim());
        return aiToolRepository.save(e);
    }

    @Transactional
    public AiToolEntity update(Long id, String name, String label, String description,
                               String httpMethod, String url, String headersJson,
                               String bodyTemplate, String contentType,
                               String methodName, String dataParamsJson,
                               String responseParamsJson, String paramsSchemaJson, Boolean enabled) {
        AiToolEntity e = aiToolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("工具不存在：" + id));
        if (StringUtils.hasText(name)) e.setName(name.trim());
        if (label != null) e.setLabel(StringUtils.hasText(label) ? label.trim() : e.getName());
        if (StringUtils.hasText(description)) e.setDescription(description.trim());
        if (StringUtils.hasText(httpMethod)) e.setHttpMethod(httpMethod.trim().toUpperCase());
        if (StringUtils.hasText(url)) e.setUrl(url.trim());
        if (headersJson != null) e.setHeadersJson(StringUtils.hasText(headersJson) ? headersJson.trim() : null);
        if (bodyTemplate != null) e.setBodyTemplate(StringUtils.hasText(bodyTemplate) ? bodyTemplate.trim() : null);
        if (contentType != null) e.setContentType(StringUtils.hasText(contentType) ? contentType.trim() : "application/json");
        if (methodName != null) e.setMethodName(StringUtils.hasText(methodName) ? methodName.trim() : null);
        if (dataParamsJson != null) e.setDataParamsJson(StringUtils.hasText(dataParamsJson) ? dataParamsJson.trim() : null);
        if (responseParamsJson != null) e.setResponseParamsJson(StringUtils.hasText(responseParamsJson) ? responseParamsJson.trim() : null);
        if (StringUtils.hasText(paramsSchemaJson)) e.setParamsSchemaJson(paramsSchemaJson.trim());
        if (enabled != null) e.setEnabled(enabled);
        return aiToolRepository.save(e);
    }

    @Transactional
    public void delete(Long id) {
        if (!aiToolRepository.existsById(id)) {
            throw new IllegalArgumentException("工具不存在：" + id);
        }
        aiAppToolRepository.deleteByToolId(id);
        aiToolRepository.deleteById(id);
    }

    @Transactional
    public void bindToolsToApp(Long appId, List<Long> toolIds) {
        aiAppToolRepository.deleteByAppId(appId);
        if (toolIds == null || toolIds.isEmpty()) return;
        int order = 0;
        for (Long toolId : toolIds) {
            if (toolId == null) continue;
            if (!aiToolRepository.existsById(toolId)) {
                throw new IllegalArgumentException("工具不存在：" + toolId);
            }
            AiAppToolEntity binding = new AiAppToolEntity();
            binding.setAppId(appId);
            binding.setToolId(toolId);
            binding.setSortOrder(order++);
            aiAppToolRepository.save(binding);
        }
    }

    private static void validate(String name, String description,
                                  String url, String paramsSchemaJson) {
        if (!StringUtils.hasText(name)) throw new IllegalArgumentException("工具名称不能为空");
        if (!StringUtils.hasText(description)) throw new IllegalArgumentException("工具描述不能为空");
        if (!StringUtils.hasText(url)) throw new IllegalArgumentException("工具 URL 不能为空");
        if (!StringUtils.hasText(paramsSchemaJson)) throw new IllegalArgumentException("参数 Schema 不能为空");
    }
}
