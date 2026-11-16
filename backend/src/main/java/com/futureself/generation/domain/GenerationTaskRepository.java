package com.futureself.generation.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GenerationTaskRepository extends JpaRepository<GenerationTask, Long> {

    // —— 可领取任务（§11.3 worker 扫描）——
    Optional<GenerationTask> findFirstByStatusOrderByCreatedAtAsc(String status);

    Optional<GenerationTask> findFirstByStatusAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(
            String status, LocalDateTime now);

    // —— 运行中任务检查（§24 幂等/防重复）——
    boolean existsByGoalIdAndStatusNotIn(Long goalId, List<String> statuses);

    /** 用户是否存在运行中任务（首页聚合 §12.7）。 */
    boolean existsByUserIdAndStatusNotIn(Long userId, List<String> statuses);

    // —— Admin（Phase 8）——

    /** 后台分页：userId/status 可空。 */
    @Query("select t from GenerationTask t where (:userId is null or t.userId = :userId) and (:status is null or t.status = :status)")
    Page<GenerationTask> adminPage(@Param("userId") Long userId, @Param("status") String status, Pageable pageable);

    long countByStatus(String status);

    // —— 受控状态迁移（乐观锁语义：仅当 from 匹配才生效，返回影响行数）——

    @Modifying
    @Query("update GenerationTask t set t.status = :to, t.progress = :progress, t.lockedAt = :now, "
            + "t.workerId = :workerId, t.startedAt = coalesce(t.startedAt, :now) "
            + "where t.id = :id and t.status = :from")
    int claim(@Param("id") Long id, @Param("from") String from, @Param("to") String to,
              @Param("progress") int progress, @Param("now") LocalDateTime now,
              @Param("workerId") String workerId);

    @Modifying
    @Query("update GenerationTask t set t.status = :status, t.progress = :progress, t.storyId = :storyId, "
            + "t.lockedAt = null, t.workerId = null "
            + "where t.id = :id and t.status = :from")
    int attachStoryAndMoveTo(@Param("id") Long id, @Param("from") String from, @Param("status") String status,
                             @Param("progress") int progress, @Param("storyId") Long storyId);

    @Modifying
    @Query("update GenerationTask t set t.status = :status, t.progress = :progress, "
            + "t.lockedAt = null, t.workerId = null "
            + "where t.id = :id and t.status = :from")
    int moveTo(@Param("id") Long id, @Param("from") String from, @Param("status") String status,
               @Param("progress") int progress);

    @Modifying
    @Query("update GenerationTask t set t.status = 'RETRY_WAIT', t.retryCount = t.retryCount + 1, "
            + "t.nextRetryAt = :nextRetryAt, t.errorStage = :stage, t.errorCode = :code, t.errorMessage = :message, "
            + "t.lockedAt = null, t.workerId = null "
            + "where t.id = :id and t.status = :from")
    int markRetry(@Param("id") Long id, @Param("from") String from, @Param("stage") String stage,
                  @Param("code") String code, @Param("message") String message,
                  @Param("nextRetryAt") LocalDateTime nextRetryAt);

    @Modifying
    @Query("update GenerationTask t set t.status = 'FAILED', t.errorStage = :stage, t.errorCode = :code, "
            + "t.errorMessage = :message, t.finishedAt = :now, t.lockedAt = null, t.workerId = null "
            + "where t.id = :id and t.status = :from")
    int markFailed(@Param("id") Long id, @Param("from") String from, @Param("stage") String stage,
                   @Param("code") String code, @Param("message") String message, @Param("now") LocalDateTime now);

    @Modifying
    @Query("update GenerationTask t set t.status = 'BLOCKED', t.errorStage = 'MODERATION', "
            + "t.errorCode = 'MODERATION_BLOCKED', t.errorMessage = :message, t.finishedAt = :now, "
            + "t.lockedAt = null, t.workerId = null "
            + "where t.id = :id and t.status = :from")
    int markBlocked(@Param("id") Long id, @Param("from") String from, @Param("message") String message,
                    @Param("now") LocalDateTime now);

    /** 任务进入终态 COMPLETED 后记录完成时间。 */
    @Modifying
    @Query("update GenerationTask t set t.finishedAt = :now where t.id = :id and t.status = 'COMPLETED' "
            + "and t.finishedAt is null")
    int markFinishedAt(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 手动重试音频（§12.5/§20.4）：文本已完成、音频失败的终态任务重置回音频提交阶段（重试预算重置）。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update GenerationTask t set t.status = 'AUDIO_SUBMITTING', t.progress = 70, t.retryCount = 0, "
            + "t.errorStage = null, t.errorCode = null, t.errorMessage = null, t.nextRetryAt = null, "
            + "t.finishedAt = null, t.lockedAt = null, t.workerId = null "
            + "where t.id = :id and t.status = :from")
    int resetToAudioSubmitting(@Param("id") Long id, @Param("from") String from);

    /** 管理员重试文本阶段（§20.4）：FAILED 文本任务重置回生成阶段（重试预算重置）。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update GenerationTask t set t.status = 'TEXT_GENERATING', t.progress = 20, t.retryCount = 0, "
            + "t.errorStage = null, t.errorCode = null, t.errorMessage = null, t.nextRetryAt = null, "
            + "t.finishedAt = null, t.lockedAt = null, t.workerId = null "
            + "where t.id = :id and t.status = :from")
    int resetToTextGenerating(@Param("id") Long id, @Param("from") String from);
}
