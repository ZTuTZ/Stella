package com.futureself.generation;

import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.config.AppGenerationProperties;
import com.futureself.generation.domain.GenerationTask;
import com.futureself.generation.domain.GenerationTaskRepository;
import com.futureself.goal.GoalService;
import com.futureself.goal.domain.ManifestationGoal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 生成任务创建与查询（实施文档 §12.4/§24）。
 * 幂等：Idempotency-Key → Redis 24h；同一 Goal 运行中任务检查（DB 为准）。
 */
@Service
public class GenerationTaskService {

    private static final Logger log = LoggerFactory.getLogger(GenerationTaskService.class);
    private static final String IDEMPOTENCY_PREFIX = "fs:idempotency:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final List<String> TERMINAL_STATUSES = List.of(
            GenerationTask.STATUS_COMPLETED, GenerationTask.STATUS_FAILED, GenerationTask.STATUS_BLOCKED);

    private final GenerationTaskRepository taskRepository;
    private final GoalService goalService;
    private final StringRedisTemplate redis;
    private final AppGenerationProperties generationProps;

    public GenerationTaskService(GenerationTaskRepository taskRepository,
                                 GoalService goalService,
                                 StringRedisTemplate redis,
                                 AppGenerationProperties generationProps) {
        this.taskRepository = taskRepository;
        this.goalService = goalService;
        this.redis = redis;
        this.generationProps = generationProps;
    }

    public record CreatedTask(long taskId, String status) {
    }

    public CreatedTask create(long userId, long goalId, String idempotencyKey) {
        String redisKey = idempotencyKey == null || idempotencyKey.isBlank()
                ? null : IDEMPOTENCY_PREFIX + userId + ":" + idempotencyKey.trim();
        if (redisKey != null) {
            String existingTaskId = redis.opsForValue().get(redisKey);
            if (existingTaskId != null) {
                GenerationTask task = taskRepository.findById(Long.parseLong(existingTaskId))
                        .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
                return new CreatedTask(task.getId(), task.getStatus());
            }
        }

        ManifestationGoal goal = goalService.loadOwnGoal(goalId, userId);
        if (taskRepository.existsByGoalIdAndStatusNotIn(goalId, TERMINAL_STATUSES)) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_RUNNING);
        }

        GenerationTask task = new GenerationTask();
        task.setUserId(userId);
        task.setGoalId(goal.getId());
        task.setStatus(GenerationTask.STATUS_PENDING);
        task.setProgress(GenerationTask.PROGRESS_PENDING);
        task.setMaxRetryCount(generationProps.maxRetryCount());
        GenerationTask saved = taskRepository.save(task);

        if (redisKey != null) {
            redis.opsForValue().set(redisKey, String.valueOf(saved.getId()), IDEMPOTENCY_TTL);
        }
        log.info("generation task created: taskId={} goalId={}", saved.getId(), goalId);
        return new CreatedTask(saved.getId(), saved.getStatus());
    }

    public GenerationTask loadOwnTask(long taskId, long userId) {
        GenerationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!task.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }

    public Map<String, Object> toResponse(GenerationTask task) {
        Map<String, Object> error = task.getErrorCode() == null ? null : Map.of(
                "stage", task.getErrorStage() == null ? "" : task.getErrorStage(),
                "code", task.getErrorCode(),
                "message", task.getErrorMessage() == null ? "" : task.getErrorMessage());
        // LinkedHashMap 允许 null 值（storyId/audioStatus 未就绪时输出 null）
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("id", task.getId());
        response.put("status", task.getStatus());
        response.put("progress", task.getProgress());
        response.put("storyId", task.getStoryId());
        response.put("audioStatus", null); // Phase 5 接入
        response.put("retryCount", task.getRetryCount());
        response.put("error", error);
        return response;
    }

    public static String newWorkerId() {
        return "worker-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }
}
