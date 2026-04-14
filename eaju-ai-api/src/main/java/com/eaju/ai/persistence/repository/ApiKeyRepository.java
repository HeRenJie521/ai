package com.eaju.ai.persistence.repository;

import com.eaju.ai.persistence.entity.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, Long> {

    List<ApiKeyEntity> findByDeletedIsFalse();

    Optional<ApiKeyEntity> findByIdAndDeletedIsFalse(Long id);

    /** 仅匹配指定 type，防止嵌入凭证被用作 API Key */
    Optional<ApiKeyEntity> findBySecretHashAndTypeAndEnabledIsTrueAndDeletedIsFalse(String secretHash, int type);

    /** WEB_EMBED：按集成 ID 查找，仅返回已启用且未删除的嵌入类型集成 */
    Optional<ApiKeyEntity> findByIdAndTypeAndEnabledIsTrueAndDeletedIsFalse(Long id, int type);
}
