package com.eaju.ai.persistence.repository;

import com.eaju.ai.persistence.entity.AdminAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 系统管理员账号 Repository
 */
@Repository
public interface AdminAccountRepository extends JpaRepository<AdminAccountEntity, Long> {

    /**
     * 根据手机号查找管理员
     */
    Optional<AdminAccountEntity> findByPhone(String phone);

    /**
     * 检查手机号是否存在
     */
    boolean existsByPhone(String phone);

    /**
     * 查询所有管理员（按创建时间倒序）
     */
    List<AdminAccountEntity> findAllByOrderByCreatedAtDesc();

    /**
     * 查询所有启用的管理员手机号（用于登录时判断）
     */
    @Query("SELECT a.phone FROM AdminAccountEntity a WHERE a.enabled = true")
    List<String> findAllEnabledAdminPhones();
}
