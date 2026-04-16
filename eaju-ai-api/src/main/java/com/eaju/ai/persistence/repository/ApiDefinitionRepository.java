package com.eaju.ai.persistence.repository;

import com.eaju.ai.persistence.entity.ApiDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 接口定义 Repository
 */
public interface ApiDefinitionRepository extends JpaRepository<ApiDefinitionEntity, Long> {

    /** 按 ID 升序返回所有接口定义 */
    List<ApiDefinitionEntity> findAllByOrderByIdAsc();
}
