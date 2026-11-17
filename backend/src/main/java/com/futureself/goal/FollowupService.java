package com.futureself.goal;

import com.futureself.ai.AiCallLogService;
import com.futureself.ai.PromptTemplateService;
import com.futureself.ai.domain.AiCallLog;
import com.futureself.ai.domain.PromptTemplate;
import com.futureself.ai.text.OpenAiCompatibleTextAiProvider;
import com.futureself.ai.text.PromptFiller;
import com.futureself.ai.text.TextAiProvider;
import com.futureself.ai.text.TextAiRequest;
import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.common.logging.RequestIdFilter;
import com.futureself.common.ratelimit.RateLimitService;
import com.futureself.config.AppTextAiProperties;
import com.futureself.config.AppRateLimitProperties;
import com.futureself.goal.api.FollowupAnswersRequest;
import com.futureself.goal.api.FollowupQuestionsResponse;
import com.futureself.goal.domain.GoalRepository;
import com.futureself.goal.domain.ManifestationGoal;
import com.futureself.moderation.ModerationProvider;
import com.futureself.moderation.ModerationRequest;
import com.futureself.profile.ProfileService;
import com.futureself.profile.api.LifeProfileResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 追问（实施文档 §5.2 步骤 D / §12.3 / §14.1）。
 * 组装用户上下文（§33：空字段不发、限长、无技术标识）→ 模板填充 →
 * TextAiProvider 结构化生成（0~3 题）→ 记录 ai_call_log → 问题/答案存 details_json。
 */
@Service
public class FollowupService {

    private static final Logger log = LoggerFactory.getLogger(FollowupService.class);
    private static final int MAX_QUESTIONS = 3;

    /** LLM 结构化输出（§14.1）。 */
    public record FollowupQuestionResult(String id, String question, String reason, Boolean optional) {
    }

    public record FollowupQuestionsResult(List<FollowupQuestionResult> questions) {
    }

    private final GoalService goalService;
    private final GoalRepository goalRepository;
    private final ProfileService profileService;
    private final PromptTemplateService promptTemplateService;
    private final TextAiProvider textAiProvider;
    private final AiCallLogService aiCallLogService;
    private final AppTextAiProperties textAiProps;
    private final PromptContextAssembler contextAssembler;
    private final RateLimitService rateLimitService;
    private final AppRateLimitProperties rateLimitProps;
    private final ModerationProvider moderationProvider;

    public FollowupService(GoalService goalService,
                           GoalRepository goalRepository,
                           ProfileService profileService,
                           PromptTemplateService promptTemplateService,
                           TextAiProvider textAiProvider,
                           AiCallLogService aiCallLogService,
                           AppTextAiProperties textAiProps,
                           PromptContextAssembler contextAssembler,
                           RateLimitService rateLimitService,
                           AppRateLimitProperties rateLimitProps,
                           ModerationProvider moderationProvider) {
        this.goalService = goalService;
        this.goalRepository = goalRepository;
        this.profileService = profileService;
        this.promptTemplateService = promptTemplateService;
        this.textAiProvider = textAiProvider;
        this.aiCallLogService = aiCallLogService;
        this.textAiProps = textAiProps;
        this.contextAssembler = contextAssembler;
        this.rateLimitService = rateLimitService;
        this.rateLimitProps = rateLimitProps;
        this.moderationProvider = moderationProvider;
    }

    @Transactional
    public FollowupQuestionsResponse generateQuestions(long goalId, long userId) {
        // 限流：AI 追问 20 次/小时/用户（§24）
        if (!rateLimitService.allowHourly(userId, "followup", rateLimitProps.followupPerHour())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
        ManifestationGoal goal = goalService.loadOwnGoal(goalId, userId);
        LifeProfileResponse profile = profileService.getProfile(userId);

        PromptTemplate template = promptTemplateService.getEnabled(PromptTemplate.KEY_FOLLOWUP);
        Map<String, String> placeholders = contextAssembler.basePlaceholders(profile, goal);
        String userPrompt = PromptFiller.fill(template.getUserTemplate(), placeholders);

        TextAiRequest request = new TextAiRequest(
                PromptTemplate.KEY_FOLLOWUP,
                template.getSystemPrompt(),
                userPrompt,
                template.getOutputSchemaJson(),
                0.5,
                800,
                currentRequestId());

        // H-09：最终上下文审核（含 profile/followup answer），拦截则不调用 LLM
        long startedAt = System.currentTimeMillis();
        if (moderationProvider.check(new ModerationRequest("FOLLOWUP_INPUT", userId, userPrompt)).blocked()) {
            recordAiCall(userId, null, request, startedAt, false, "MODERATION_BLOCKED", null);
            throw new BusinessException(ErrorCode.MODERATION_BLOCKED);
        }
        FollowupQuestionsResult result;
        try {
            result = textAiProvider.generateStructured(request, FollowupQuestionsResult.class);
        } catch (BusinessException e) {
            recordAiCall(userId, null, request, startedAt, false, e.errorCode().name(), e.getMessage());
            throw e;
        }
        recordAiCall(userId, null, request, startedAt, true, null, null);

        List<FollowupQuestionResult> normalized = normalize(result == null ? null : result.questions());
        persistQuestions(goal, normalized);
        log.info("followup questions generated: goalId={} count={}", goalId, normalized.size());

        return new FollowupQuestionsResponse(normalized.stream()
                .map(q -> new FollowupQuestionsResponse.QuestionItem(
                        q.id(), q.question(), "TEXT", q.optional() == null || q.optional()))
                .toList());
    }

    @Transactional
    public com.futureself.goal.api.GoalResponse saveAnswers(long goalId, long userId, FollowupAnswersRequest request) {
        ManifestationGoal goal = goalService.loadOwnGoal(goalId, userId);
        Map<String, Object> details = new LinkedHashMap<>(JsonSupport.parseObject(goal.getDetailsJson()));

        List<Map<String, String>> answers = new ArrayList<>();
        if (request.answers() != null) {
            for (FollowupAnswersRequest.AnswerItem item : request.answers()) {
                answers.add(Map.of(
                        "questionId", item.questionId(),
                        "answer", item.answer() == null ? "" : item.answer()));
            }
        }
        details.put("answers", answers);
        goal.setDetailsJson(JsonSupport.write(details));
        return goalService.toResponse(goalRepository.save(goal));
    }

    /** 校验/规整模型输出：去空、限 3 题、补 id（q1..qn）、optional 默认 true。 */
    private List<FollowupQuestionResult> normalize(List<FollowupQuestionResult> questions) {
        List<FollowupQuestionResult> cleaned = new ArrayList<>();
        if (questions != null) {
            for (FollowupQuestionResult q : questions) {
                if (q == null || q.question() == null || q.question().isBlank()) {
                    continue;
                }
                cleaned.add(q);
                if (cleaned.size() == MAX_QUESTIONS) {
                    break;
                }
            }
        }
        List<FollowupQuestionResult> result = new ArrayList<>();
        for (int i = 0; i < cleaned.size(); i++) {
            FollowupQuestionResult q = cleaned.get(i);
            String id = (q.id() == null || q.id().isBlank()) ? "q" + (i + 1) : q.id();
            result.add(new FollowupQuestionResult(id, q.question().trim(), q.reason(), q.optional()));
        }
        return result;
    }

    private void persistQuestions(ManifestationGoal goal, List<FollowupQuestionResult> questions) {
        Map<String, Object> details = new LinkedHashMap<>(JsonSupport.parseObject(goal.getDetailsJson()));
        List<Map<String, Object>> list = new ArrayList<>();
        for (FollowupQuestionResult q : questions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", q.id());
            item.put("question", q.question());
            item.put("reason", q.reason());
            item.put("optional", q.optional() == null || q.optional());
            list.add(item);
        }
        details.put("questions", list);
        goal.setDetailsJson(JsonSupport.write(details));
        goalRepository.save(goal);
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private String currentRequestId() {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        return requestId == null ? "" : requestId;
    }

    private void recordAiCall(long userId, Long taskId, TextAiRequest request,
                              long startedAt, boolean success, String errorCode, String errorMessage) {
        long latency = System.currentTimeMillis() - startedAt;
        Integer[] usage = pollUsageOrNull();
        aiCallLogService.record(new AiCallLogService.CallRecord(
                currentRequestId(),
                userId,
                taskId,
                AiCallLog.TYPE_TEXT,
                textAiProvider.providerName(),
                textAiProps.model(),
                request.operation(),
                latency,
                success,
                usage == null ? null : usage[0],
                usage == null ? null : usage[1],
                errorCode,
                errorMessage,
                requestSummary(request),
                null));
    }

    /** OpenAI-compatible provider 会把 usage 放入 ThreadLocal；mock 无 usage。 */
    private Integer[] pollUsageOrNull() {
        int[] raw = OpenAiCompatibleTextAiProvider.UsageHolder.poll();
        if (raw == null || raw[0] < 0) {
            return null;
        }
        return new Integer[]{raw[0], raw[1]};
    }

    /** H-07：只存 operation / 字符数 / 指纹，不保存 prompt 正文。 */
    private String requestSummary(TextAiRequest request) {
        try {
            return JsonSupport.write(com.futureself.ai.AiLogSanitizer.promptSummary(
                    orEmpty(request.operation()), request.systemPrompt(), request.userPrompt()));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
