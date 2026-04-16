package com.eaju.ai.persistence.repository;

import com.eaju.ai.persistence.entity.UserContextFieldEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserContextFieldRepository extends JpaRepository<UserContextFieldEntity, Long> {

    List<UserContextFieldEntity> findAllByOrderByIdAsc();

    List<UserContextFieldEntity> findByEnabledIsTrueOrderByIdAsc();

    Optional<UserContextFieldEntity> findByFieldKey(String fieldKey);
}
