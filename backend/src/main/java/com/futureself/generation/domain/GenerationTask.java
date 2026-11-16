package com.futureself.generation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 生成任务（generation_task，实施文档 §10.6/§11）。
 * MySQL 任务队列表为事实源；worker 单实例乐观锁推进，重启可恢复。
 */
@Entity
@Table(name = "generation_task", indexes = {
        @Index(name = "idx_task_status_retry", columnList = "status, next_retry_at, created_at"),
        @Index(name = "idx_task_user", columnList = "user_id, created_at")
})
public class GenerationTask {

    // —— 状态机（§11.1）——
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_TEXT_GENERATING = "TEXT_GENERATING";
    public static final String STATUS_TEXT_MODERATING = "TEXT_MODERATING";
    public static final String STATUS_TEXT_READY = "TEXT_READY";
    public static final String STATUS_AUDIO_SUBMITTING = "AUDIO_SUBMITTING";
    public static final String STATUS_AUDIO_PROCESSING = "AUDIO_PROCESSING";
    public static final String STATUS_AUDIO_STORING = "AUDIO_STORING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_RETRY_WAIT = "RETRY_WAIT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_BLOCKED = "BLOCKED";

    // —— 阶段（error_stage，§10.6）——
    public static final String STAGE_TEXT = "TEXT";
    public static final String STAGE_MODERATION = "MODERATION";
    public static final String STAGE_AUDIO = "AUDIO";
    public static final String STAGE_STORAGE = "STORAGE";

    public static final String TYPE_STORY_WITH_AUDIO = "STORY_WITH_AUDIO";

    // —— 展示进度（§10.6 progress 0~100）——
    public static final int PROGRESS_PENDING = 5;
    public static final int PROGRESS_TEXT_GENERATING = 20;
    public static final int PROGRESS_TEXT_MODERATING = 40;
    public static final int PROGRESS_TEXT_READY = 60;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "goal_id", nullable = false)
    private Long goalId;

    @Column(name = "story_id")
    private Long storyId;

    @Column(name = "task_type", nullable = false, length = 30)
    private String taskType = TYPE_STORY_WITH_AUDIO;

    @Column(nullable = false, length = 40)
    private String status = STATUS_PENDING;

    @Column(nullable = false)
    private int progress = PROGRESS_PENDING;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "max_retry_count", nullable = false)
    private int maxRetryCount = 3;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "worker_id", length = 100)
    private String workerId;

    @Column(name = "error_stage", length = 40)
    private String errorStage;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getGoalId() {
        return goalId;
    }

    public void setGoalId(Long goalId) {
        this.goalId = goalId;
    }

    public Long getStoryId() {
        return storyId;
    }

    public void setStoryId(Long storyId) {
        this.storyId = storyId;
    }

    public String getTaskType() {
        return taskType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(LocalDateTime lockedAt) {
        this.lockedAt = lockedAt;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public String getErrorStage() {
        return errorStage;
    }

    public void setErrorStage(String errorStage) {
        this.errorStage = errorStage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
