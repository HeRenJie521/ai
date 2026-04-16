package com.eaju.ai.service;

import com.eaju.ai.persistence.entity.ApiDefinitionEntity;
import com.eaju.ai.persistence.repository.ApiDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 接口定义管理服务
 */
@Service
public class ApiDefinitionService {

    private final ApiDefinitionRepository apiDefinitionRepository;

    public ApiDefinitionService(ApiDefinitionRepository apiDefinitionRepository) {
        this.apiDefinitionRepository = apiDefinitionRepository;
    }

    /**
     * 获取所有接口定义列表
     */
    @Transactional(readOnly = true)
    public List<ApiDefinitionEntity> listAll() {
        return apiDefinitionRepository.findAllByOrderByIdAsc();
    }

    /**
     * 创建接口定义
     */
    @Transactional
    public ApiDefinitionEntity create(String systemName, String requestUrl, String httpMethod, String contentType, String remark) {
        validate(systemName, requestUrl, contentType);
        ApiDefinitionEntity entity = new ApiDefinitionEntity();
        entity.setSystemName(systemName.trim());
        entity.setRequestUrl(requestUrl.trim());
        entity.setHttpMethod(StringUtils.hasText(httpMethod) ? httpMethod.trim().toUpperCase() : "POST");
        entity.setContentType(contentType.trim());
        entity.setRemark(StringUtils.hasText(remark) ? remark.trim() : null);
        return apiDefinitionRepository.save(entity);
    }

    /**
     * 更新接口定义
     */
    @Transactional
    public ApiDefinitionEntity update(Long id, String systemName, String requestUrl, String httpMethod, String contentType, String remark) {
        ApiDefinitionEntity entity = apiDefinitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("接口定义不存在：" + id));
        if (StringUtils.hasText(systemName)) entity.setSystemName(systemName.trim());
        if (StringUtils.hasText(requestUrl)) entity.setRequestUrl(requestUrl.trim());
        if (StringUtils.hasText(httpMethod)) entity.setHttpMethod(httpMethod.trim().toUpperCase());
        if (StringUtils.hasText(contentType)) entity.setContentType(contentType.trim());
        entity.setRemark(StringUtils.hasText(remark) ? remark.trim() : null);
        return apiDefinitionRepository.save(entity);
    }

    /**
     * 删除接口定义
     */
    @Transactional
    public void delete(Long id) {
        if (!apiDefinitionRepository.existsById(id)) {
            throw new IllegalArgumentException("接口定义不存在：" + id);
        }
        apiDefinitionRepository.deleteById(id);
    }

    private static void validate(String systemName, String requestUrl, String contentType) {
        if (!StringUtils.hasText(systemName)) throw new IllegalArgumentException("系统名称不能为空");
        if (!StringUtils.hasText(requestUrl)) throw new IllegalArgumentException("接口请求路径不能为空");
        if (!StringUtils.hasText(contentType)) throw new IllegalArgumentException("参数格式不能为空");
    }
}
