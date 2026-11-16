package com.futureself.admin.application;

import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.generation.domain.GenerationTask;
import com.futureself.generation.domain.GenerationTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

/**
 * 管理后台任务服务（§20.4）：任务列表 + 手动重试。
 * 重试规则：
 *  - 运行中（PENDING / TEXT_GENERATING / TEXT_MODERATING / TEXT_READY / AUDIO_* / RETRY_WAIT）→ TASK_ALREADY_RUNNING
 *  - BLOCKED（审核拦截）→ 不可重试（MODERATION_BLOCKED）
 *  - storyId 存在（文本已完成）→ 只重试音频（resetToAudioSubmitting）
 *  - storyId 为空（文本阶段失败）→ 重置回文本生成（resetToTextGenerating）
 * 手动重试会重置重试预算；频次由 retry-audio 限流另行约束（§24）。
 */
@Service
public class AdminTaskService {

    private static final Set<String> RUNNING_STATUSES = Set.of(
            GenerationTask.STATUS_PENDING,
            GenerationTask.STATUS_TEXT_GENERATING,
            GenerationTask.STATUS_TEXT_MODERATING,
            GenerationTask.STATUS_TEXT_READY,
            GenerationTask.STATUS_AUDIO_SUBMITTING,
            GenerationTask.STATUS_AUDIO_PROCESSING,
            GenerationTask.STATUS_AUDIO_STORING,
            GenerationTask.STATUS_RETRY_WAIT);

    private final GenerationTaskRepository taskRepository;

    public AdminTaskService(GenerationTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public GenerationTask load(long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
    }

    @Transactional
    public Map<String, Object> retry(long taskId) {
        GenerationTask task = load(taskId);
        String status = task.getStatus();
        if (RUNNING_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_RUNNING);
        }
        if (GenerationTask.STATUS_BLOCKED.equals(status)) {
            throw new BusinessException(ErrorCode.MODERATION_BLOCKED, "content blocked task cannot be retried");
        }
        String from = status;
        if (task.getStoryId() != null) {
            // 文本已完成：仅重试音频（旧音频将被 SUPERSEDED）
            taskRepository.resetToAudioSubmitting(taskId, from);
        } else {
            taskRepository.resetToTextGenerating(taskId, from);
        }
        GenerationTask after = load(taskId);
        return Map.of(
                "id", after.getId(),
                "status", after.getStatus(),
                "retryCount", after.getRetryCount());
    }
}
