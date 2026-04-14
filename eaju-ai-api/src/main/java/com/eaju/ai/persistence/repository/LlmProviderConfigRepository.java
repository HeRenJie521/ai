package com.eaju.ai.persistence.repository;

import com.eaju.ai.persistence.entity.LlmProviderConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LlmProviderConfigRepository extends JpaRepository<LlmProviderConfigEntity, Long> {

    Optional<LlmProviderConfigEntity> findByCodeIgnoreCase(String code);

    List<LlmProviderConfigEntity> findAllByOrderBySortOrderAscCodeAsc();

    boolean existsByCodeIgnoreCase(String code);

    List<LlmProviderConfigEntity> findAllByEnabledTrueOrderBySortOrderAscCodeAsc();
}
