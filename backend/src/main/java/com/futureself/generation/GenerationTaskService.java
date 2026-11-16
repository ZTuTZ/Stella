package com.futureself.generation;

import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.config.AppGenerationProperties;
import com.futureself.audio.domain.AudioAsset;
import com.futureself.audio.domain.AudioAssetRepository;
import com.futureself.generation.domain.GenerationTask;
import com.futureself.generation.domain.GenerationTaskRepository;
import com.futureself.goal.GoalService;
import com.futureself.goal.domain.ManifestationGoal;
import com.futureself.story.domain.Story;
import com.futureself.story.domain.StoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 生成任务创建与查询（实施文档 §12.4/§24）。
 * 幂等：Idempotency-Key → Redis 24h；同一 Goal 运行中任务检查（DB 为准）。
 * 音频手动重试（§12.5/§20.4）：文本完成、音频失败的终态任务重置回音频提交阶段。
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
    private final StoryRepository storyRepository;
    private final AudioAssetRepository audioAssetRepository;
    private final StringRedisTemplate redis;
    private final AppGenerationProperties generationProps;
    private final TransactionTemplate transactionTemplate;

    public GenerationTaskService(GenerationTaskRepository taskRepository,
                                 GoalService goalService,
                                 StoryRepository storyRepository,
                                 AudioAssetRepository audioAssetRepository,
                                 StringRedisTemplate redis,
                                 AppGenerationProperties generationProps,
                                 TransactionTemplate transactionTemplate) {
        this.taskRepository = taskRepository;
        this.goalService = goalService;
        this.storyRepository = storyRepository;
        this.audioAssetRepository = audioAssetRepository;
        this.redis = redis;
        this.generationProps = generationProps;
        this.transactionTemplate = transactionTemplate;
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
        String audioStatus = task.getStoryId() == null ? null : audioAssetRepository
                .findTopByStoryIdAndStatusNotOrderByIdDesc(task.getStoryId(), AudioAsset.STATUS_SUPERSEDED)
                .map(AudioAsset::getStatus).orElse(null);
        // LinkedHashMap 允许 null 值（storyId/audioStatus 未就绪时输出 null）
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("id", task.getId());
        response.put("status", task.getStatus());
        response.put("progress", task.getProgress());
        response.put("storyId", task.getStoryId());
        response.put("audioStatus", audioStatus);
        response.put("retryCount", task.getRetryCount());
        response.put("error", error);
        return response;
    }

    /** 音频手动重试（§12.5）：返回 {taskId, taskStatus, audioStatus}。 */
    public Map<String, Object> retryAudio(long userId, long storyId) {
        Story story = storyRepository
                .findByIdAndUserIdAndStatus(storyId, userId, Story.STATUS_ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORY_NOT_FOUND));
        if (Story.MODERATION_BLOCKED.equals(story.getModerationStatus())) {
            throw new BusinessException(ErrorCode.MODERATION_BLOCKED);
        }
        if (story.getGenerationTaskId() == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        GenerationTask task = loadOwnTask(story.getGenerationTaskId(), userId);
        String current = task.getStatus();
        if (GenerationTask.STATUS_AUDIO_SUBMITTING.equals(current)
                || GenerationTask.STATUS_AUDIO_PROCESSING.equals(current)
                || GenerationTask.STATUS_AUDIO_STORING.equals(current)
                || GenerationTask.STATUS_TEXT_READY.equals(current)) {
            // 音频已在途或即将开始：幂等返回
            return retryResponse(task);
        }
        // 终态（FAILED/COMPLETED/BLOCKED 音频阶段）→ 重置回音频提交（§20.4 只重试音频）
        Integer rows = transactionTemplate.execute(tx ->
                taskRepository.resetToAudioSubmitting(task.getId(), current));
        if (rows == null || rows == 0) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_RUNNING);
        }
        log.info("audio retry scheduled: taskId={} storyId={} from={}", task.getId(), storyId, current);
        return retryResponse(taskRepository.findById(task.getId()).orElseThrow());
    }

    private Map<String, Object> retryResponse(GenerationTask task) {
        String audioStatus = task.getStoryId() == null ? null : audioAssetRepository
                .findTopByStoryIdAndStatusNotOrderByIdDesc(task.getStoryId(), AudioAsset.STATUS_SUPERSEDED)
                .map(AudioAsset::getStatus).orElse(null);
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("taskId", task.getId());
        response.put("taskStatus", task.getStatus());
        response.put("audioStatus", audioStatus);
        return response;
    }

    public static String newWorkerId() {
        return "worker-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }
}
