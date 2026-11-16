package com.futureself.generation;

import com.futureself.ai.AiCallLogService;
import com.futureself.ai.PromptTemplateService;
import com.futureself.ai.domain.AiCallLog;
import com.futureself.ai.domain.PromptTemplate;
import com.futureself.ai.text.PromptFiller;
import com.futureself.ai.text.TextAiProvider;
import com.futureself.ai.text.TextAiRequest;
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
import com.futureself.user.UserService;
import com.futureself.user.domain.AppUser;
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
 * 失败 → RETRY_WAIT（退避）→ 超限 FAILED；审核拦截 → BLOCKED；
 * 生成内容在进入审核阶段前已落库（重启可恢复，§28 Phase 4 验收）。
 */
@Component
public class GenerationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GenerationOrchestrator.class);
    private static final int SUMMARY_LIMIT = 500;

    private final GenerationTaskRepository taskRepository;
    private final StoryRepository storyRepository;
    private final GoalService goalService;
    private final ProfileService profileService;
    private final UserService userService;
    private final PromptTemplateService promptTemplateService;
    private final TextAiProvider textAiProvider;
    private final AiCallLogService aiCallLogService;
    private final ModerationProvider moderationProvider;
    private final PromptContextAssembler contextAssembler;
    private final AppGenerationProperties generationProps;
    private final AppTextAiProperties textAiProps;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public GenerationOrchestrator(GenerationTaskRepository taskRepository,
                                  StoryRepository storyRepository,
                                  GoalService goalService,
                                  ProfileService profileService,
                                  UserService userService,
                                  PromptTemplateService promptTemplateService,
                                  TextAiProvider textAiProvider,
                                  AiCallLogService aiCallLogService,
                                  ModerationProvider moderationProvider,
                                  PromptContextAssembler contextAssembler,
                                  AppGenerationProperties generationProps,
                                  AppTextAiProperties textAiProps,
                                  ObjectMapper objectMapper,
                                  TransactionTemplate transactionTemplate) {
        this.taskRepository = taskRepository;
        this.storyRepository = storyRepository;
        this.goalService = goalService;
        this.profileService = profileService;
        this.userService = userService;
        this.promptTemplateService = promptTemplateService;
        this.textAiProvider = textAiProvider;
        this.aiCallLogService = aiCallLogService;
        this.moderationProvider = moderationProvider;
        this.contextAssembler = contextAssembler;
        this.generationProps = generationProps;
        this.textAiProps = textAiProps;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
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
            String backTo = GenerationTask.STAGE_MODERATION.equals(task.getErrorStage())
                    ? GenerationTask.STATUS_TEXT_MODERATING
                    : GenerationTask.STATUS_TEXT_GENERATING;
            Integer rows = transactionTemplate.execute(tx ->
                    taskRepository.claim(task.getId(), GenerationTask.STATUS_RETRY_WAIT, backTo,
                            progressOf(backTo), now, GenerationTaskService.newWorkerId()));
            if (rows != null && rows > 0) {
                executeStage(backTo, task.getId());
                return true;
            }
        }

        // 2) 待领取任务
        for (String status : java.util.List.of(
                GenerationTask.STATUS_PENDING,
                GenerationTask.STATUS_TEXT_GENERATING,
                GenerationTask.STATUS_TEXT_MODERATING)) {
            Optional<GenerationTask> candidate = taskRepository.findFirstByStatusOrderByCreatedAtAsc(status);
            if (candidate.isEmpty()) {
                continue;
            }
            GenerationTask task = candidate.get();
            String next = switch (status) {
                case GenerationTask.STATUS_PENDING -> GenerationTask.STATUS_TEXT_GENERATING;
                default -> status; // TEXT_GENERATING / TEXT_MODERATING 直接执行当前阶段
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

    private int progressOf(String status) {
        return switch (status) {
            case GenerationTask.STATUS_TEXT_GENERATING -> GenerationTask.PROGRESS_TEXT_GENERATING;
            case GenerationTask.STATUS_TEXT_MODERATING -> GenerationTask.PROGRESS_TEXT_MODERATING;
            case GenerationTask.STATUS_TEXT_READY -> GenerationTask.PROGRESS_TEXT_READY;
            default -> GenerationTask.PROGRESS_PENDING;
        };
    }

    private void executeStage(String stage, long taskId) {
        try {
            switch (stage) {
                case GenerationTask.STATUS_TEXT_GENERATING -> executeTextGeneration(taskId);
                case GenerationTask.STATUS_TEXT_MODERATING -> executeModeration(taskId);
                default -> log.warn("unknown stage {} for task {}", stage, taskId);
            }
        } catch (RuntimeException e) {
            // 兜底：未预期的异常也走失败/重试通道，任务不卡死
            log.warn("stage {} threw {}: taskId={}", stage, e.getClass().getSimpleName(), taskId);
            handleFailure(taskId, stage, ErrorCode.INTERNAL_ERROR.name(),
                    "unexpected error: " + e.getClass().getSimpleName());
        }
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
            recordAiCall(task, template, request, startedAt, false, e.errorCode().name(), e.getMessage());
            handleFailure(taskId, GenerationTask.STAGE_TEXT, e.errorCode().name(), e.getMessage());
            return;
        }
        recordAiCall(task, template, request, startedAt, true, null, null);

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
        log.info("story generated: taskId={} storyDraftId={}", taskId, taskRepository.findById(taskId).map(GenerationTask::getStoryId).orElse(null));
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
    }

    /** 失败处理（§11.1）：未超限 → RETRY_WAIT（指数退避）；超限 → FAILED。 */
    private void handleFailure(long taskId, String stage, String errorCode, String errorMessage) {
        GenerationTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        String from = switch (stage) {
            case GenerationTask.STAGE_MODERATION -> GenerationTask.STATUS_TEXT_MODERATING;
            case GenerationTask.STAGE_TEXT -> GenerationTask.STATUS_TEXT_GENERATING;
            default -> task.getStatus();
        };
        if (task.getRetryCount() >= task.getMaxRetryCount()) {
            Integer rows = transactionTemplate.execute(tx -> taskRepository.markFailed(
                    taskId, from, stage, errorCode, truncate(errorMessage, 1000), LocalDateTime.now()));
            log.warn("task failed: taskId={} stage={} code={} rows={}", taskId, stage, errorCode, rows);
            return;
        }
        long backoffMs = generationProps.retryBackoffBaseMs() * (1L << task.getRetryCount());
        LocalDateTime nextRetryAt = LocalDateTime.now().plusNanos(backoffMs * 1_000_000);
        Integer rows = transactionTemplate.execute(tx -> taskRepository.markRetry(
                taskId, from, stage, errorCode, truncate(errorMessage, 1000), nextRetryAt));
        log.info("task scheduled retry: taskId={} stage={} attempt={} nextRetryAt={} rows={}",
                taskId, stage, task.getRetryCount() + 1, nextRetryAt, rows);
    }

    private void recordAiCall(GenerationTask task, PromptTemplate template, TextAiRequest request,
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

    private String requestSummary(TextAiRequest request) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "operation", request.operation() == null ? "" : request.operation(),
                    "promptTemplateKey", PromptTemplate.KEY_STORY,
                    "systemPrompt", PromptFiller.truncate(request.systemPrompt(), 200),
                    "userPrompt", PromptFiller.truncate(request.userPrompt(), 200)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String currentRequestId() {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        return requestId == null ? "" : requestId;
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
