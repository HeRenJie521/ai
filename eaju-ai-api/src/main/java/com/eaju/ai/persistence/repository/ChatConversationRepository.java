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

    /** 列表查询：仅返回在 chat 页面直接创建的会话（apiKeyId、integrationId、appId 均为 null） */
    List<ChatConversationEntity> findByUserIdAndApiKeyIdIsNullAndIntegrationIdIsNullAndAppIdIsNullAndDeletedAtIsNullOrderByLastMessageAtDesc(String userId);

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

    /** 列表查询：按 integrationId + userId，仅返回当前用户自己的未删除会话 */
    List<ChatConversationEntity> findByIntegrationIdAndUserIdAndDeletedAtIsNullOrderByLastMessageAtDesc(Long integrationId, String userId);

    /** 按 integrationId + sessionId 查（仅未删除），用于 WEB_EMBED 会话归属校验 */
    Optional<ChatConversationEntity> findByIntegrationIdAndSessionIdAndDeletedAtIsNull(Long integrationId, String sessionId);

    /**
     * 列表查询：按 appId + userId，兼容两种数据：
     * 1. 新数据：app_id = ?（76531dd 之后正确落库）
     * 2. 老数据：app_id IS NULL、api_key_id IS NULL、integration_id IS NULL（之前未回填）
     * 两者均过滤 deleted_at IS NULL，按 last_message_at 倒序。
     */
    @Query("SELECT c FROM ChatConversationEntity c WHERE c.userId = :userId " +
           "AND (c.appId = :appId OR (c.appId IS NULL AND c.apiKeyId IS NULL AND c.integrationId IS NULL)) " +
           "AND c.deletedAt IS NULL ORDER BY c.lastMessageAt DESC")
    List<ChatConversationEntity> findConversationsForAppUser(@Param("userId") String userId,
                                                             @Param("appId") Long appId);

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
