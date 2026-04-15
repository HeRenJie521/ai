package com.eaju.ai.persistence.repository;

import com.eaju.ai.persistence.entity.AiAppEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiAppRepository extends JpaRepository<AiAppEntity, Long> {

    List<AiAppEntity> findByDeletedIsFalseOrderByCreatedAtDesc();

    Optional<AiAppEntity> findByIdAndDeletedIsFalse(Long id);
}
