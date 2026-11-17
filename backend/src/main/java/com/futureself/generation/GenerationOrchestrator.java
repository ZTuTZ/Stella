package com.futureself.generation;

import com.futureself.ai.AiCallLogService;
import com.futureself.ai.PromptTemplateService;
import com.futureself.ai.audio.AiAudioProvider;
import com.futureself.ai.audio.AudioGenerationRequest;
import com.futureself.ai.audio.AudioMode;
import com.futureself.ai.audio.AudioQueryResult;
import com.futureself.ai.audio.AudioSubmitResult;
import com.futureself.ai.domain.AiCallLog;
import com.futureself.ai.domain.PromptTemplate;
import com.futureself.ai.text.PromptFiller;
import com.futureself.ai.text.TextAiProvider;
import com.futureself.ai.text.TextAiRequest;
import com.futureself.audio.domain.AudioAsset;
import com.futureself.audio.domain.AudioAssetRepository;
import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.common.logging.RequestIdFilter;
import com.futureself.config.AppGenerationProperties;
import com.futureself.config.AppTextAiProperties;
import com.futureself.generation.domain.GenerationTask;
import com.futureself.generation.domain.GenerationTaskRepository;
import com.futureself.goal.GoalService;
import com.futureself.goal.PromptContextAssembler;
import com.futureself.goal.domain.ManifestationGoal;
import com.futureself.moderation.ModerationProvider;
import com.futureself.moderation.ModerationRequest;
import com.futureself.profile.ProfileService;
import com.futureself.profile.api.LifeProfileResponse;
import com.futureself.story.domain.Story;
import com.futureself.story.domain.StoryRepository;
import com.futureself.user.UserEventService;
import com.futureself.user.UserService;
import com.futureself.user.domain.AppUser;
import com.futureself.config.AppAudioProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 生成任务编排器（实施文档 §11）：
 * 每次调用 processNext() 只推进一个任务的一个阶段；
 * AI 调用不在事务内（§11.3），受控状态迁移用乐观锁 guarded update；
 * 文本：PENDING → TEXT_GENERATING → TEXT_MODERATING → TEXT_READY（先落库再审核，重启可恢复）；
 * 音频：TEXT_READY → AUDIO_SUBMITTING → AUDIO_PROCESSING → AUDIO_STORING → COMPLETED；
 * 失败 → RETRY_WAIT（退避）→ 超限 FAILED；审核拦截 → BLOCKED。
 */
@Component
public class GenerationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GenerationOrchestrator.class);

    private final GenerationTaskRepository taskRepository;
    private final StoryRepository storyRepository;
    private final AudioAssetRepository audioAssetRepository;
    private final GoalService goalService;
    private final ProfileService profileService;
    private final UserService userService;
    private final PromptTemplateService promptTemplateService;
    private final TextAiProvider textAiProvider;
    private final AiCallLogService aiCallLogService;
    private final ModerationProvider moderationProvider;
    private final PromptContextAssembler contextAssembler;
    private final AiAudioProvider audioProvider;
    private final AudioScriptBuilder audioScriptBuilder;
    private final AudioTransferService audioTransferService;
    private final AppGenerationProperties generationProps;
    private final AppTextAiProperties textAiProps;
    private final AppAudioProperties audioProps;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final UserEventService userEventService;

    public GenerationOrchestrator(GenerationTaskRepository taskRepository,
                                  StoryRepository storyRepository,
                                  AudioAssetRepository audioAssetRepository,
                                  GoalService goalService,
                                  ProfileService profileService,
                                  UserService userService,
                                  PromptTemplateService promptTemplateService,
                                  TextAiProvider textAiProvider,
                                  AiCallLogService aiCallLogService,
                                  ModerationProvider moderationProvider,
                                  PromptContextAssembler contextAssembler,
                                  AiAudioProvider audioProvider,
                                  AudioScriptBuilder audioScriptBuilder,
                                  AudioTransferService audioTransferService,
                                  AppGenerationProperties generationProps,
                                  AppTextAiProperties textAiProps,
                                  AppAudioProperties audioProps,
                                  ObjectMapper objectMapper,
                                  TransactionTemplate transactionTemplate,
                                  UserEventService userEventService) {
        this.taskRepository = taskRepository;
        this.storyRepository = storyRepository;
        this.audioAssetRepository = audioAssetRepository;
        this.goalService = goalService;
        this.profileService = profileService;
        this.userService = userService;
        this.promptTemplateService = promptTemplateService;
        this.textAiProvider = textAiProvider;
        this.aiCallLogService = aiCallLogService;
        this.moderationProvider = moderationProvider;
        this.contextAssembler = contextAssembler;
        this.audioProvider = audioProvider;
        this.audioScriptBuilder = audioScriptBuilder;
        this.audioTransferService = audioTransferService;
        this.generationProps = generationProps;
        this.textAiProps = textAiProps;
        this.audioProps = audioProps;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.userEventService = userEventService;
    }

    /** 推进一个可执行任务的一个阶段；无任务返回 false。 */
    public boolean processNext() {
        LocalDateTime now = LocalDateTime.now();

        // 1) 到期的重试任务（RETRY_WAIT → 回到失败阶段，§11.1）
        Optional<GenerationTask> retryDue =
                taskRepository.findFirstByStatusAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(
                        GenerationTask.STATUS_RETRY_WAIT, now);
        if (retryDue.isPresent()) {
            GenerationTask task = retryDue.get();
            String backTo = backToStage(task.getErrorStage());
            Integer rows = transactionTemplate.execute(tx ->
                    taskRepository.claim(task.getId(), GenerationTask.STATUS_RETRY_WAIT, backTo,
                            progressOf(backTo), now, GenerationTaskService.newWorkerId()));
            if (rows != null && rows > 0) {
                executeStage(backTo, task.getId());
                return true;
            }
        }

        // 2) 待领取任务（含 TEXT_READY → AUDIO_SUBMITTING 的音频启动）
        for (String status : java.util.List.of(
                GenerationTask.STATUS_PENDING,
                GenerationTask.STATUS_TEXT_GENERATING,
                GenerationTask.STATUS_TEXT_MODERATING,
                GenerationTask.STATUS_TEXT_READY,
                GenerationTask.STATUS_AUDIO_SUBMITTING,
                GenerationTask.STATUS_AUDIO_PROCESSING,
                GenerationTask.STATUS_AUDIO_STORING)) {
            Optional<GenerationTask> candidate = taskRepository.findFirstByStatusOrderByCreatedAtAsc(status);
            if (candidate.isEmpty()) {
                continue;
            }
            GenerationTask task = candidate.get();
            String next = switch (status) {
                case GenerationTask.STATUS_PENDING -> GenerationTask.STATUS_TEXT_GENERATING;
                case GenerationTask.STATUS_TEXT_READY -> GenerationTask.STATUS_AUDIO_SUBMITTING;
                default -> status; // 其余为“执行当前阶段”
            };
            boolean claimed;
            if (next.equals(status)) {
                claimed = true; // 已处于该阶段（重启恢复场景），直接执行
            } else {
                Integer rows = transactionTemplate.execute(tx ->
                        taskRepository.claim(task.getId(), status, next,
                                progressOf(next), now, GenerationTaskService.newWorkerId()));
                claimed = rows != null && rows > 0;
            }
            if (claimed) {
                executeStage(next, task.getId());
                return true;
            }
        }
        return false;
    }

    private String backToStage(String errorStage) {
        return switch (errorStage == null ? "" : errorStage) {
            case GenerationTask.STAGE_MODERATION -> GenerationTask.STATUS_TEXT_MODERATING;
            case GenerationTask.STAGE_AUDIO -> GenerationTask.STATUS_AUDIO_SUBMITTING;
            // H-04：转存失败只重试转存，复用同一 providerUrl，绝不重新提交 TTS
            case GenerationTask.STAGE_STORAGE -> GenerationTask.STATUS_AUDIO_STORING;
            default -> GenerationTask.STATUS_TEXT_GENERATING;
        };
    }

    private int progressOf(String status) {
        return switch (status) {
            case GenerationTask.STATUS_TEXT_GENERATING -> GenerationTask.PROGRESS_TEXT_GENERATING;
            case GenerationTask.STATUS_TEXT_MODERATING -> GenerationTask.PROGRESS_TEXT_MODERATING;
            case GenerationTask.STATUS_TEXT_READY -> GenerationTask.PROGRESS_TEXT_READY;
            case GenerationTask.STATUS_AUDIO_SUBMITTING -> GenerationTask.PROGRESS_AUDIO_SUBMITTING;
            case GenerationTask.STATUS_AUDIO_PROCESSING -> GenerationTask.PROGRESS_AUDIO_PROCESSING;
            case GenerationTask.STATUS_AUDIO_STORING -> GenerationTask.PROGRESS_AUDIO_STORING;
            case GenerationTask.STATUS_COMPLETED -> GenerationTask.PROGRESS_COMPLETED;
            default -> GenerationTask.PROGRESS_PENDING;
        };
    }

    private void executeStage(String stage, long taskId) {
        try {
            switch (stage) {
                case GenerationTask.STATUS_TEXT_GENERATING -> executeTextGeneration(taskId);
                case GenerationTask.STATUS_TEXT_MODERATING -> executeModeration(taskId);
                case GenerationTask.STATUS_AUDIO_SUBMITTING -> executeAudioSubmit(taskId);
                case GenerationTask.STATUS_AUDIO_PROCESSING -> executeAudioQuery(taskId);
                case GenerationTask.STATUS_AUDIO_STORING -> executeAudioStore(taskId);
                default -> log.warn("unknown stage {} for task {}", stage, taskId);
            }
        } catch (RuntimeException e) {
            // 兜底：未预期的异常也走失败/重试通道，任务不卡死
            log.warn("stage {} threw {}: taskId={}", stage, e.getClass().getSimpleName(), taskId);
            handleFailure(taskId, stageForStatus(stage), ErrorCode.INTERNAL_ERROR.name(),
                    "unexpected error: " + e.getClass().getSimpleName());
        }
    }

    private String stageForStatus(String status) {
        return switch (status) {
            case GenerationTask.STATUS_TEXT_MODERATING -> GenerationTask.STAGE_MODERATION;
            case GenerationTask.STATUS_AUDIO_SUBMITTING, GenerationTask.STATUS_AUDIO_PROCESSING ->
                    GenerationTask.STAGE_AUDIO;
            case GenerationTask.STATUS_AUDIO_STORING -> GenerationTask.STAGE_STORAGE;
            default -> GenerationTask.STAGE_TEXT;
        };
    }

    /** 文本生成（§14.2）。AI 调用不在事务内；结果落库后才进入审核阶段。 */
    private void executeTextGeneration(long taskId) {
        GenerationTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        ManifestationGoal goal = goalService.loadOwnGoal(task.getGoalId(), task.getUserId());
        LifeProfileResponse profile = profileService.getProfile(task.getUserId());
        AppUser user = userService.findById(task.getUserId()).orElse(null);
        String displayName = user == null ? "" : user.getDisplayName();

        PromptTemplate template = promptTemplateService.getEnabled(PromptTemplate.KEY_STORY);
        Map<String, String> placeholders = contextAssembler.storyPlaceholders(displayName, profile, goal);
        String userPrompt = PromptFiller.fill(template.getUserTemplate(), placeholders);

        TextAiRequest request = new TextAiRequest(
                PromptTemplate.KEY_STORY,
                template.getSystemPrompt(),
                userPrompt,
                template.getOutputSchemaJson(),
                0.8,
                3000,
                currentRequestId());

        long startedAt = System.currentTimeMillis();
        StoryGenerationResult result;
        try {
            result = textAiProvider.generateStructured(request, StoryGenerationResult.class);
        } catch (BusinessException e) {
            recordTextCall(task, request, startedAt, false, e.errorCode().name(), e.getMessage());
            handleFailure(taskId, GenerationTask.STAGE_TEXT, e.errorCode().name(), e.getMessage());
            return;
        }
        recordTextCall(task, request, startedAt, true, null, null);

        if (result == null || isBlank(result.title()) || isBlank(result.summary()) || isBlank(result.content())) {
            handleFailure(taskId, GenerationTask.STAGE_TEXT,
                    ErrorCode.TEXT_AI_INVALID_RESPONSE.name(), "story result missing fields");
            return;
        }

        // 结果落库（story 以 PENDING 审核态存在；进入审核阶段，重启可恢复）
        transactionTemplate.executeWithoutResult(tx -> {
            Story story = new Story();
            story.setUserId(task.getUserId());
            story.setGoalId(task.getGoalId());
            story.setGenerationTaskId(task.getId());
            story.setTitle(truncate(result.title(), 200));
            story.setSummary(truncate(result.summary(), 500));
            story.setContent(result.content());
            story.setPerspective(Story.PERSPECTIVE_SECOND_PERSON);
            story.setPromptTemplateId(template.getId());
            story.setProvider(textAiProvider.providerName());
            story.setModel(textAiProps.model());
            story.setProfileVersion(profile.profileVersion());
            story.setStatus(Story.STATUS_ACTIVE);
            story.setModerationStatus(Story.MODERATION_PENDING);
            Story saved = storyRepository.save(story);
            Integer rows = taskRepository.attachStoryAndMoveTo(task.getId(),
                    GenerationTask.STATUS_TEXT_GENERATING,
                    GenerationTask.STATUS_TEXT_MODERATING,
                    GenerationTask.PROGRESS_TEXT_MODERATING,
                    saved.getId());
            if (rows == null || rows == 0) {
                throw new IllegalStateException("task moved concurrently: " + task.getId());
            }
        });
        log.info("story generated: taskId={} storyDraftId={}", taskId,
                taskRepository.findById(taskId).map(GenerationTask::getStoryId).orElse(null));
    }

    /** 输出审核（§19.2 阶段 2）：拦截 → BLOCKED；通过 → TEXT_READY。 */
    private void executeModeration(long taskId) {
        GenerationTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null || task.getStoryId() == null) {
            log.warn("moderation stage without story: taskId={}", taskId);
            return;
        }
        Story story = storyRepository.findById(task.getStoryId()).orElse(null);
        if (story == null) {
            handleFailure(taskId, GenerationTask.STAGE_MODERATION,
                    ErrorCode.INTERNAL_ERROR.name(), "story draft missing");
            return;
        }

        ModerationRequest request = new ModerationRequest("STORY_OUTPUT", task.getUserId(),
                story.getTitle() + "\n" + story.getSummary() + "\n" + story.getContent());
        long startedAt = System.currentTimeMillis();
        var result = moderationProvider.check(request);
        log.info("story moderation: taskId={} blocked={} latencyMs={}",
                taskId, result.blocked(), System.currentTimeMillis() - startedAt);

        if (result.blocked()) {
            transactionTemplate.executeWithoutResult(tx -> {
                story.setStatus(Story.STATUS_BLOCKED);
                story.setModerationStatus(Story.MODERATION_BLOCKED);
                storyRepository.save(story);
                Integer rows = taskRepository.markBlocked(task.getId(),
                        GenerationTask.STATUS_TEXT_MODERATING, result.reason(), LocalDateTime.now());
                if (rows == null || rows == 0) {
                    throw new IllegalStateException("task moved concurrently: " + task.getId());
                }
            });
            return;
        }

        transactionTemplate.executeWithoutResult(tx -> {
            story.setModerationStatus(Story.MODERATION_PASSED);
            storyRepository.save(story);
            Integer rows = taskRepository.moveTo(task.getId(),
                    GenerationTask.STATUS_TEXT_MODERATING,
                    GenerationTask.STATUS_TEXT_READY,
                    GenerationTask.PROGRESS_TEXT_READY);
            if (rows == null || rows == 0) {
                throw new IllegalStateException("task moved concurrently: " + task.getId());
            }
        });
        log.info("task text ready: taskId={} storyId={}", taskId, story.getId());
        userEventService.record(task.getUserId(), UserEventService.EVENT_STORY_TEXT_READY, "STORY", story.getId(),
                Map.of("taskId", task.getId()));
    }

    /** 音频提交（§15.2）：脚本处理 → submit → 建 audio_asset（SUBMITTED）→ AUDIO_PROCESSING。 */
    private void executeAudioSubmit(long taskId) {
        GenerationTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null || task.getStoryId() == null) {
            log.warn("audio submit without story: taskId={}", taskId);
            return;
        }
        Story story = storyRepository.findById(task.getStoryId()).orElse(null);
        if (story == null) {
            handleFailure(taskId, GenerationTask.STAGE_AUDIO,
                    ErrorCode.STORY_NOT_FOUND.name(), "story missing for audio");
            return;
        }

        String script = audioScriptBuilder.build(story.getContent());
        AudioGenerationRequest request = new AudioGenerationRequest(
                currentRequestId(), story.getId(), script,
                emptyToNull(audioProps.voiceId()), null, null, AudioMode.NARRATION, Map.of());

        long startedAt = System.currentTimeMillis();
        AudioSubmitResult submitted;
        try {
            submitted = audioProvider.submit(request);
        } catch (BusinessException e) {
            log.warn("audio submit failed: taskId={} code={}", taskId, e.errorCode());
            recordAudioCall(task, "AUDIO_SUBMIT", startedAt, false, e.errorCode().name(), e.getMessage());
            handleFailure(taskId, GenerationTask.STAGE_AUDIO, e.errorCode().name(), e.getMessage());
            return;
        }
        recordAudioCall(task, "AUDIO_SUBMIT", startedAt, true, null, null);
        log.info("audio submitted: taskId={} latencyMs={}", taskId, System.currentTimeMillis() - startedAt);

        transactionTemplate.executeWithoutResult(tx -> {
            // H-05 blue-green：旧 READY 音频保持可播放，等新音频真正 READY 后再原子切换
            AudioAsset asset = new AudioAsset();
            asset.setUserId(task.getUserId());
            asset.setStoryId(story.getId());
            asset.setProvider(audioProvider.providerName());
            asset.setProviderTaskId(submitted == null ? null : submitted.providerTaskId());
            asset.setMode(AudioMode.NARRATION.name());
            asset.setVoiceId(emptyToNull(audioProps.voiceId()));
            asset.setStatus(submitted == null ? AudioAsset.STATUS_PROCESSING : AudioAsset.STATUS_SUBMITTED);
            asset.setRequestJson(requestSummary(script));
            audioAssetRepository.save(asset);
            Integer rows = taskRepository.moveTo(task.getId(),
                    GenerationTask.STATUS_AUDIO_SUBMITTING,
                    GenerationTask.STATUS_AUDIO_PROCESSING,
                    GenerationTask.PROGRESS_AUDIO_PROCESSING);
            if (rows == null || rows == 0) {
                throw new IllegalStateException("task moved concurrently: " + task.getId());
            }
        });
    }

    /** 音频查询：PROCESSING 保持等待；READY → AUDIO_STORING；FAILED → 重试通道。 */
    private void executeAudioQuery(long taskId) {
        GenerationTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null || task.getStoryId() == null) {
            return;
        }
        AudioAsset asset = audioAssetRepository
                .findTopByStoryIdAndStatusNotOrderByIdDesc(task.getStoryId(), AudioAsset.STATUS_SUPERSEDED)
                .orElse(null);
        if (asset == null || asset.getProviderTaskId() == null) {
            // 无在途音频（如重启丢失）：重新提交
            Integer rows = transactionTemplate.execute(tx -> taskRepository.moveTo(task.getId(),
                    GenerationTask.STATUS_AUDIO_PROCESSING, GenerationTask.STATUS_AUDIO_SUBMITTING,
                    GenerationTask.PROGRESS_AUDIO_SUBMITTING));
            if (rows == null || rows == 0) {
                handleFailure(taskId, GenerationTask.STAGE_AUDIO, "AUDIO_SUBMIT_FAILED", "no in-flight audio");
            }
            return;
        }

        long queryStartedAt = System.currentTimeMillis();
        AudioQueryResult query;
        try {
            query = audioProvider.query(asset.getProviderTaskId());
            recordAudioCall(task, "AUDIO_QUERY", queryStartedAt, true, null, null);
        } catch (BusinessException e) {
            recordAudioCall(task, "AUDIO_QUERY", queryStartedAt, false, e.errorCode().name(), e.getMessage());
            handleFailure(taskId, GenerationTask.STAGE_AUDIO, e.errorCode().name(), e.getMessage());
            return;
        }
        if (query == null || query.status() == AudioQueryResult.JobStatus.PROCESSING) {
            return; // 下一个 tick 再查
        }
        if (query.status() == AudioQueryResult.JobStatus.FAILED) {
            markAssetFailed(asset, query.errorCode() == null ? "AUDIO_PROCESSING_FAILED" : query.errorCode(),
                    query.errorMessage());
            handleFailure(taskId, GenerationTask.STAGE_AUDIO,
                    query.errorCode() == null ? "AUDIO_PROCESSING_FAILED" : query.errorCode(),
                    query.errorMessage());
            return;
        }

        // READY：保存供应商 URL（仅内部使用），进入转存阶段
        transactionTemplate.executeWithoutResult(tx -> {
            asset.setProviderUrl(query.url());
            asset.setFormat(query.format() == null ? asset.getFormat() : query.format());
            asset.setDurationMs(query.durationMs());
            asset.setStatus(AudioAsset.STATUS_PROCESSING);
            audioAssetRepository.save(asset);
            Integer rows = taskRepository.moveTo(task.getId(),
                    GenerationTask.STATUS_AUDIO_PROCESSING,
                    GenerationTask.STATUS_AUDIO_STORING,
                    GenerationTask.PROGRESS_AUDIO_STORING);
            if (rows == null || rows == 0) {
                throw new IllegalStateException("task moved concurrently: " + task.getId());
            }
        });
    }

    /** 音频转存（§15.5 第 6 条）：READY 后立即转存自有存储 → COMPLETED。 */
    private void executeAudioStore(long taskId) {
        GenerationTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null || task.getStoryId() == null) {
            return;
        }
        AudioAsset asset = audioAssetRepository
                .findTopByStoryIdAndStatusNotOrderByIdDesc(task.getStoryId(), AudioAsset.STATUS_SUPERSEDED)
                .orElse(null);
        if (asset == null || asset.getProviderUrl() == null) {
            handleFailure(taskId, GenerationTask.STAGE_STORAGE,
                    ErrorCode.AUDIO_STORAGE_FAILED.name(), "provider url missing");
            return;
        }
        try {
            var stored = audioTransferService.transfer(task.getUserId(), task.getStoryId(), new AudioQueryResult(
                    AudioQueryResult.JobStatus.READY, asset.getProviderUrl(), asset.getFormat(),
                    asset.getDurationMs(), null, null));
            transactionTemplate.executeWithoutResult(tx -> {
                // 先把其他 READY 音频标记 SUPERSEDED（blue-green 原子切换）
                for (AudioAsset other : audioAssetRepository.findByStoryId(asset.getStoryId())) {
                    if (AudioAsset.STATUS_READY.equals(other.getStatus()) && !other.getId().equals(asset.getId())) {
                        other.setStatus(AudioAsset.STATUS_SUPERSEDED);
                        audioAssetRepository.save(other);
                    }
                }
                asset.setStorageKey(stored.key());
                asset.setPublicUrl(stored.url());
                asset.setProviderUrl(null); // H-03：转存成功后清理供应商临时 URL
                asset.setStatus(AudioAsset.STATUS_READY);
                audioAssetRepository.save(asset);
                Integer rows = taskRepository.moveTo(task.getId(),
                        GenerationTask.STATUS_AUDIO_STORING,
                        GenerationTask.STATUS_COMPLETED,
                        GenerationTask.PROGRESS_COMPLETED);
                if (rows == null || rows == 0) {
                    throw new IllegalStateException("task moved concurrently: " + task.getId());
                }
                taskRepository.markFinishedAt(task.getId(), LocalDateTime.now());
            });
            log.info("audio ready: taskId={} storyId={} storageKey={}", taskId, task.getStoryId(), stored.key());
            userEventService.record(task.getUserId(), UserEventService.EVENT_AUDIO_READY, "STORY", task.getStoryId(),
                    Map.of("taskId", task.getId(), "format", asset.getFormat()));
        } catch (BusinessException e) {
            // H-04：保留 PROCESSING + providerUrl，下一次 RETRY_WAIT 只重试转存
            handleFailure(taskId, GenerationTask.STAGE_STORAGE, e.errorCode().name(), e.getMessage());
        }
    }

    /** 失败处理（§11.1）：未超限 → RETRY_WAIT（指数退避）；超限 → FAILED。 */
    private void handleFailure(long taskId, String stage, String errorCode, String errorMessage) {
        GenerationTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        String from = task.getStatus();
        if (task.getRetryCount() >= task.getMaxRetryCount()) {
            Integer rows = transactionTemplate.execute(tx -> taskRepository.markFailed(
                    taskId, from, stage, errorCode, truncate(errorMessage, 1000), LocalDateTime.now()));
            log.warn("task failed: taskId={} stage={} code={} rows={}", taskId, stage, errorCode, rows);
            if (GenerationTask.STAGE_STORAGE.equals(stage) && task.getStoryId() != null) {
                audioAssetRepository
                        .findTopByStoryIdAndStatusNotOrderByIdDesc(task.getStoryId(), AudioAsset.STATUS_SUPERSEDED)
                        .ifPresent(a -> markAssetFailed(a, errorCode, errorMessage));
            }
            return;
        }
        long backoffMs = generationProps.retryBackoffBaseMs() * (1L << task.getRetryCount());
        LocalDateTime nextRetryAt = LocalDateTime.now().plusNanos(backoffMs * 1_000_000);
        Integer rows = transactionTemplate.execute(tx -> taskRepository.markRetry(
                taskId, from, stage, errorCode, truncate(errorMessage, 1000), nextRetryAt));
        log.info("task scheduled retry: taskId={} stage={} attempt={} nextRetryAt={} rows={}",
                taskId, stage, task.getRetryCount() + 1, nextRetryAt, rows);
    }

    private void markAssetFailed(AudioAsset asset, String code, String message) {
        transactionTemplate.executeWithoutResult(tx -> {
            asset.setStatus(AudioAsset.STATUS_FAILED);
            asset.setErrorCode(code);
            asset.setErrorMessage(truncate(message, 1000));
            audioAssetRepository.save(asset);
        });
    }

    private void recordTextCall(GenerationTask task, TextAiRequest request,
                                long startedAt, boolean success, String errorCode, String errorMessage) {
        aiCallLogService.record(new AiCallLogService.CallRecord(
                currentRequestId(),
                task.getUserId(),
                task.getId(),
                AiCallLog.TYPE_TEXT,
                textAiProvider.providerName(),
                textAiProps.model(),
                request.operation(),
                System.currentTimeMillis() - startedAt,
                success,
                null,
                null,
                errorCode,
                errorMessage,
                requestSummary(request),
                null));
    }

    /** 音频 AI 调用日志（§10.9）：provider_type=AUDIO，便于 dashboard 平均耗时与链路排查。 */
    private void recordAudioCall(GenerationTask task, String operation, long startedAt,
                                 boolean success, String errorCode, String errorMessage) {
        aiCallLogService.record(new AiCallLogService.CallRecord(
                currentRequestId(),
                task.getUserId(),
                task.getId(),
                AiCallLog.TYPE_AUDIO,
                audioProvider.providerName(),
                null,
                operation,
                System.currentTimeMillis() - startedAt,
                success,
                null,
                null,
                errorCode,
                errorMessage,
                null,
                null));
    }

    private String requestSummary(TextAiRequest request) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "operation", request.operation() == null ? "" : request.operation(),
                    "systemPrompt", PromptFiller.truncate(request.systemPrompt(), 200),
                    "userPrompt", PromptFiller.truncate(request.userPrompt(), 200)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String requestSummary(String script) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "textLength", script == null ? 0 : script.length(),
                    "textPreview", PromptFiller.truncate(script, 200)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String currentRequestId() {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        return requestId == null ? "" : requestId;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value == null ? "" : value;
        }
        return value.substring(0, max);
    }
}
