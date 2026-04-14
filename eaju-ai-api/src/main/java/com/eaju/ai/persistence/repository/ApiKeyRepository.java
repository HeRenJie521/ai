package com.eaju.ai.persistence.repository;

import com.eaju.ai.persistence.entity.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, Long> {

    List<ApiKeyEntity> findByDeletedIsFalse();

    Optional<ApiKeyEntity> findByIdAndDeletedIsFalse(Long id);

    Optional<ApiKeyEntity> findBySecretHashAndEnabledIsTrueAndDeletedIsFalse(String secretHash);
}
