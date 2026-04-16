package com.eaju.ai.persistence.repository;

import com.eaju.ai.persistence.entity.ChatConversationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatConversationRepository extends JpaRepository<ChatConversationEntity, Long> {

    /** 列表查询：仅返回未删除的会话 */
    List<ChatConversationEntity> findByUserIdAndDeletedAtIsNullOrderByLastMessageAtDesc(String userId);

    /** 按 userId + sessionId 查（不过滤删除状态，供内部写操作使用） */
    Optional<ChatConversationEntity> findByUserIdAndSessionId(String userId, String sessionId);

    /** 按 userId + sessionId 查（仅未删除） */
    Optional<ChatConversationEntity> findByUserIdAndSessionIdAndDeletedAtIsNull(String userId, String sessionId);

    /** 列表查询：仅返回未删除的会话（API Key 维度） */
    List<ChatConversationEntity> findByApiKeyIdAndDeletedAtIsNullOrderByLastMessageAtDesc(Long apiKeyId);

    /** 按 apiKeyId + sessionId 查（不过滤删除状态，供内部写操作使用） */
    Optional<ChatConversationEntity> findByApiKeyIdAndSessionId(Long apiKeyId, String sessionId);

    /** 按 apiKeyId + sessionId 查（仅未删除） */
    Optional<ChatConversationEntity> findByApiKeyIdAndSessionIdAndDeletedAtIsNull(Long apiKeyId, String sessionId);

    /** 逻辑删除：设置 deleted_at */
    @Modifying
    @Query("UPDATE ChatConversationEntity c SET c.deletedAt = :now " +
           "WHERE c.userId = :userId AND c.sessionId = :sessionId AND c.deletedAt IS NULL")
    int softDeleteByUserIdAndSessionId(@Param("userId") String userId,
                                       @Param("sessionId") String sessionId,
                                       @Param("now") Instant now);

    /** 查询所有会话（包括已删除），分页 */
    Page<ChatConversationEntity> findAllByOrderByLastMessageAtDesc(Pageable pageable);

    /** 按手机号查询会话（包括已删除），分页 */
    Page<ChatConversationEntity> findByUserIdOrderByLastMessageAtDesc(String userId, Pageable pageable);

    /** 按 API Key 查询会话（包括已删除），分页 */
    Page<ChatConversationEntity> findByApiKeyIdOrderByLastMessageAtDesc(Long apiKeyId, Pageable pageable);

    /** 统计用户会话数（包括已删除） */
    long countByUserId(String userId);

    /** 统计 API Key 关联的会话数（包括已删除） */
    long countByApiKeyId(Long apiKeyId);

    /** 根据 sessionId 查询会话（包括已删除） */
    Optional<ChatConversationEntity> findBySessionId(String sessionId);

    /** 列表查询：仅返回未删除的 embed 会话（按最新消息倒序） */
    List<ChatConversationEntity> findByIntegrationIdAndDeletedAtIsNullOrderByLastMessageAtDesc(Long integrationId);

    /** 按 integrationId + sessionId 查（仅未删除），用于 WEB_EMBED 会话归属校验 */
    Optional<ChatConversationEntity> findByIntegrationIdAndSessionIdAndDeletedAtIsNull(Long integrationId, String sessionId);

    /** 逻辑删除（embed 维度）：仅删除属于该 integrationId 的会话 */
    @Modifying
    @Query("UPDATE ChatConversationEntity c SET c.deletedAt = :now " +
           "WHERE c.integrationId = :integrationId AND c.sessionId = :sessionId AND c.deletedAt IS NULL")
    int softDeleteByIntegrationIdAndSessionId(@Param("integrationId") Long integrationId,
                                              @Param("sessionId") String sessionId,
                                              @Param("now") Instant now);

    /** 按 appId 查询会话（包括已删除），分页 */
    Page<ChatConversationEntity> findByAppIdOrderByLastMessageAtDesc(Long appId, Pageable pageable);

    /** 统计 AI 应用关联的会话数（包括已删除） */
    long countByAppId(Long appId);
}
