package com.eaju.ai.persistence.repository;

import com.eaju.ai.persistence.entity.ChatTurnEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatTurnRepository extends JpaRepository<ChatTurnEntity, Long> {

    List<ChatTurnEntity> findBySessionIdAndUserIdOrderByCreatedAtAsc(String sessionId, String userId);

    List<ChatTurnEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    void deleteBySessionIdAndUserId(String sessionId, String userId);

    long countByApiKeyId(Long apiKeyId);

    @Query("SELECT COALESCE(SUM(t.promptTokens), 0) FROM ChatTurnEntity t WHERE t.apiKeyId = :ak")
    long sumPromptTokensByApiKeyId(@Param("ak") Long apiKeyId);

    @Query("SELECT COALESCE(SUM(t.completionTokens), 0) FROM ChatTurnEntity t WHERE t.apiKeyId = :ak")
    long sumCompletionTokensByApiKeyId(@Param("ak") Long apiKeyId);

    @Query("SELECT COALESCE(SUM(t.totalTokens), 0) FROM ChatTurnEntity t WHERE t.apiKeyId = :ak")
    long sumTotalTokensByApiKeyId(@Param("ak") Long apiKeyId);

    @Query("SELECT t.model, COUNT(t), COALESCE(SUM(t.totalTokens), 0) FROM ChatTurnEntity t WHERE t.apiKeyId = :ak GROUP BY t.model")
    List<Object[]> aggregateByModel(@Param("ak") Long apiKeyId);

    List<ChatTurnEntity> findTop50ByApiKeyIdOrderByCreatedAtDesc(Long apiKeyId);

    /** 统计会话的 Token 用量 */
    @Query("SELECT COALESCE(SUM(t.promptTokens), 0), COALESCE(SUM(t.completionTokens), 0), COALESCE(SUM(t.totalTokens), 0) FROM ChatTurnEntity t WHERE t.sessionId = :sessionId")
    List<Object[]> sumTokensBySessionId(@Param("sessionId") String sessionId);

    /** 按会话统计各模型用量 */
    @Query("SELECT t.model, COUNT(t), COALESCE(SUM(t.promptTokens), 0), COALESCE(SUM(t.completionTokens), 0), COALESCE(SUM(t.totalTokens), 0) FROM ChatTurnEntity t WHERE t.sessionId = :sessionId GROUP BY t.model")
    List<Object[]> aggregateBySessionId(@Param("sessionId") String sessionId);
}
