package com.eaju.ai.service;

import com.eaju.ai.persistence.entity.UserContextFieldEntity;
import com.eaju.ai.persistence.repository.UserContextFieldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class UserContextFieldService {

    private final UserContextFieldRepository repository;

    public UserContextFieldService(UserContextFieldRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<UserContextFieldEntity> listAll() {
        return repository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public List<UserContextFieldEntity> listEnabled() {
        return repository.findByEnabledIsTrueOrderByIdAsc();
    }

    @Transactional
    public UserContextFieldEntity create(String fieldKey, String label, String description) {
        if (!StringUtils.hasText(fieldKey)) throw new IllegalArgumentException("fieldKey 不能为空");
        if (!StringUtils.hasText(label)) throw new IllegalArgumentException("label 不能为空");
        if (repository.findByFieldKey(fieldKey.trim()).isPresent()) {
            throw new IllegalArgumentException("字段 key 已存在: " + fieldKey.trim());
        }
        UserContextFieldEntity e = new UserContextFieldEntity();
        e.setFieldKey(fieldKey.trim());
        e.setLabel(label.trim());
        e.setDescription(StringUtils.hasText(description) ? description.trim() : null);
        return repository.save(e);
    }

    @Transactional
    public UserContextFieldEntity update(Long id, String fieldKey, String label, String description, Boolean enabled) {
        UserContextFieldEntity e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("上下文字段不存在: " + id));
        if (StringUtils.hasText(fieldKey)) {
            String newKey = fieldKey.trim();
            if (!newKey.equals(e.getFieldKey())) {
                if (repository.findByFieldKey(newKey).isPresent()) {
                    throw new IllegalArgumentException("字段 key 已存在: " + newKey);
                }
                e.setFieldKey(newKey);
            }
        }
        if (StringUtils.hasText(label)) {
            e.setLabel(label.trim());
        }
        if (description != null) {
            e.setDescription(StringUtils.hasText(description) ? description.trim() : null);
        }
        if (enabled != null) {
            e.setEnabled(enabled);
        }
        return repository.save(e);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("上下文字段不存在: " + id);
        }
        repository.deleteById(id);
    }
}
