package com.eaju.ai.persistence.repository;

import com.eaju.ai.persistence.entity.LlmModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LlmModelRepository extends JpaRepository<LlmModelEntity, Long> {

    List<LlmModelEntity> findByProviderIdOrderBySortOrderAscNameAsc(Long providerId);

    List<LlmModelEntity> findByProviderIdAndEnabledTrueOrderBySortOrderAscNameAsc(Long providerId);

    @Query("SELECT m FROM LlmModelEntity m JOIN LlmProviderConfigEntity p ON m.providerId = p.id " +
           "WHERE upper(p.code) = upper(:providerCode) AND m.name = :modelName AND m.enabled = true")
    Optional<LlmModelEntity> findByProviderCodeAndName(
            @Param("providerCode") String providerCode,
            @Param("modelName") String modelName);

    @Query("SELECT m FROM LlmModelEntity m JOIN LlmProviderConfigEntity p ON m.providerId = p.id " +
           "WHERE upper(p.code) = upper(:providerCode) AND m.enabled = true " +
           "ORDER BY m.sortOrder ASC, m.name ASC")
    List<LlmModelEntity> findEnabledByProviderCode(@Param("providerCode") String providerCode);
}
