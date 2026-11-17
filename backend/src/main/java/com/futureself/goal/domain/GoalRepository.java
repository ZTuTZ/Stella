package com.futureself.goal.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<ManifestationGoal, Long> {

    /** 创建生成任务时的并发保护（H-06）：对 goal 行加悲观写锁。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from ManifestationGoal g where g.id = :id")
    Optional<ManifestationGoal> findByIdForUpdate(@Param("id") Long id);

    /** 用户自己的未删除目标，按创建时间倒序（§10.3 索引 idx_goal_user_status）。 */
    List<ManifestationGoal> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    /** 最近一个 ACTIVE 目标（Affirmation 上下文用）。 */
    Optional<ManifestationGoal> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    /** 后台分页：userId/status 可空。 */
    @Query("select g from ManifestationGoal g where (:userId is null or g.userId = :userId) and (:status is null or g.status = :status)")
    Page<ManifestationGoal> adminPage(@Param("userId") Long userId, @Param("status") String status, Pageable pageable);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
