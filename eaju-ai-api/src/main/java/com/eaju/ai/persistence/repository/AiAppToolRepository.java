package com.eaju.ai.persistence.repository;

import com.eaju.ai.persistence.entity.AiAppToolEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiAppToolRepository extends JpaRepository<AiAppToolEntity, Long> {

    List<AiAppToolEntity> findByAppIdOrderBySortOrderAscIdAsc(Long appId);

    void deleteByAppId(Long appId);

    void deleteByToolId(Long toolId);
}
