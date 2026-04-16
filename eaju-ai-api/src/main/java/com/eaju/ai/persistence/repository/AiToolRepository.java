package com.eaju.ai.persistence.repository;

import com.eaju.ai.persistence.entity.AiToolEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AiToolRepository extends JpaRepository<AiToolEntity, Long> {

    List<AiToolEntity> findAllByOrderByIdAsc();

    List<AiToolEntity> findByEnabledIsTrueOrderByIdAsc();

    /** 查询某应用绑定的所有工具（启用状态），按绑定排序 */
    @Query("SELECT t FROM AiToolEntity t " +
           "JOIN AiAppToolEntity binding ON binding.toolId = t.id " +
           "WHERE binding.appId = :appId AND t.enabled = true " +
           "ORDER BY binding.sortOrder ASC, binding.id ASC")
    List<AiToolEntity> findEnabledToolsByAppId(@Param("appId") Long appId);

    /** 查询某应用绑定的所有工具（不过滤启用状态） */
    @Query("SELECT t FROM AiToolEntity t " +
           "JOIN AiAppToolEntity binding ON binding.toolId = t.id " +
           "WHERE binding.appId = :appId " +
           "ORDER BY binding.sortOrder ASC, binding.id ASC")
    List<AiToolEntity> findAllToolsByAppId(@Param("appId") Long appId);
}
