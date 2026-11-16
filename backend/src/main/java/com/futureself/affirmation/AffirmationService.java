package com.futureself.affirmation;

import com.futureself.ai.AiCallLogService;
import com.futureself.ai.PromptTemplateService;
import com.futureself.ai.domain.AiCallLog;
import com.futureself.ai.domain.PromptTemplate;
import com.futureself.ai.text.PromptFiller;
import com.futureself.ai.text.TextAiProvider;
import com.futureself.ai.text.TextAiRequest;
import com.futureself.affirmation.api.AffirmationResponse;
import com.futureself.affirmation.domain.AffirmationDaily;
import com.futureself.affirmation.domain.AffirmationDailyRepository;
import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.common.logging.RequestIdFilter;
import com.futureself.config.AppTextAiProperties;
import com.futureself.goal.PromptContextAssembler;
import com.futureself.goal.domain.GoalRepository;
import com.futureself.goal.domain.ManifestationGoal;
import com.futureself.moderation.ModerationProvider;
import com.futureself.moderation.ModerationRequest;
import com.futureself.profile.ProfileService;
import com.futureself.profile.api.LifeProfileResponse;
import com.futureself.user.UserService;
import com.futureself.user.domain.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 每日肯定句（实施文档 §9.8/§12.6/§14.3）。
 * 当日幂等：先查库；不存在时生成一次；并发由 UNIQUE(user_id, local_date) 兜底（§12.6）。
 * 输出审核（§19.2 阶段 3）：拦截 → MODERATION_BLOCKED，不落库。
 * Redis 缓存（§17 fs:affirmation:*）为可选优化，V1 直接以数据库为事实源。
 */
@Service
public class AffirmationService {

    private static final Logger log = LoggerFactory.getLogger(AffirmationService.class);
    private static final int EXPECTED_ITEMS = 6;

    /** AFFIRMATION 结构化输出（§14.3）。 */
    public record AffirmationItemsResult(List<String> items) {
    }

    private final AffirmationDailyRepository repository;
    private final PromptTemplateService promptTemplateService;
    private final TextAiProvider textAiProvider;
    private final AiCallLogService aiCallLogService;
    private final ModerationProvider moderationProvider;
    private final ProfileService profileService;
    private final UserService userService;
    private final GoalRepository goalRepository;
    private final PromptContextAssembler contextAssembler;
    private final AppTextAiProperties textAiProps;
    private final ObjectMapper objectMapper;

    public AffirmationService(AffirmationDailyRepository repository,
                              PromptTemplateService promptTemplateService,
                              TextAiProvider textAiProvider,
                              AiCallLogService aiCallLogService,
                              ModerationProvider moderationProvider,
                              ProfileService profileService,
                              UserService userService,
                              GoalRepository goalRepository,
                              PromptContextAssembler contextAssembler,
                              AppTextAiProperties textAiProps,
                              ObjectMapper objectMapper) {
        this.repository = repository;
        this.promptTemplateService = promptTemplateService;
        this.textAiProvider = textAiProvider;
        this.aiCallLogService = aiCallLogService;
        this.moderationProvider = moderationProvider;
        this.profileService = profileService;
        this.userService = userService;
        this.goalRepository = goalRepository;
        this.contextAssembler = contextAssembler;
        this.textAiProps = textAiProps;
        this.objectMapper = objectMapper;
    }

    /** 当日肯定句；不存在则生成一次（AI 调用不进事务）。 */
    public AffirmationResponse today(long userId) {
        LocalDate today = LocalDate.now();
        var existing = repository.findByUserIdAndLocalDate(userId, today);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        List<String> items = generateOnce(userId, today);
        try {
            AffirmationDaily saved = persist(userId, today, items);
            return toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            // 并发：另一个请求已生成 → 以库内记录为准（§12.6）
            log.info("affirmation concurrent insert resolved by unique index: userId={}", userId);
            return toResponse(repository.findByUserIdAndLocalDate(userId, today).orElseThrow());
        }
    }

    private List<String> generateOnce(long userId, LocalDate today) {
        LifeProfileResponse profile = profileService.getProfile(userId);
        AppUser user = userService.findById(userId).orElse(null);
        String displayName = user == null ? "" : user.getDisplayName();
        ManifestationGoal goal = goalRepository
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, ManifestationGoal.STATUS_ACTIVE)
                .orElse(null);

        PromptTemplate template = promptTemplateService.getEnabled(PromptTemplate.KEY_AFFIRMATION);
        Map<String, String> placeholders = new LinkedHashMap<>(
                contextAssembler.basePlaceholders(profile, goal == null ? emptyGoal(userId) : goal));
        placeholders.put("displayName", displayName == null ? "" : displayName);

        TextAiRequest request = new TextAiRequest(
                PromptTemplate.KEY_AFFIRMATION,
                template.getSystemPrompt(),
                PromptFiller.fill(template.getUserTemplate(), placeholders),
                template.getOutputSchemaJson(),
                0.9,
                600,
                currentRequestId());

        long startedAt = System.currentTimeMillis();
        AffirmationItemsResult result;
        try {
            result = textAiProvider.generateStructured(request, AffirmationItemsResult.class);
        } catch (BusinessException e) {
            recordCall(userId, request, startedAt, false, e.errorCode().name(), e.getMessage());
            throw e;
        }
        recordCall(userId, request, startedAt, true, null, null);

        List<String> items = result == null || result.items() == null ? List.of() : result.items();
        if (items.size() < EXPECTED_ITEMS) {
            throw new BusinessException(ErrorCode.TEXT_AI_INVALID_RESPONSE, "affirmation items insufficient");
        }
        // §19.2 阶段 3：Affirmation 输出审核
        var moderation = moderationProvider.check(new ModerationRequest("AFFIRMATION_OUTPUT", userId,
                String.join("\n", items)));
        if (moderation.blocked()) {
            log.warn("affirmation blocked: userId={} category={}", userId, moderation.category());
            throw new BusinessException(ErrorCode.MODERATION_BLOCKED);
        }
        return items.stream().limit(EXPECTED_ITEMS).toList();
    }

    private ManifestationGoal emptyGoal(long userId) {
        // 无目标时仍可基于人生档案生成；占位对象不落库
        ManifestationGoal placeholder = new ManifestationGoal();
        placeholder.setUserId(userId);
        return placeholder;
    }

    @Transactional
    protected AffirmationDaily persist(long userId, LocalDate today, List<String> items) {
        PromptTemplate template = promptTemplateService.getEnabled(PromptTemplate.KEY_AFFIRMATION);
        AffirmationDaily entity = new AffirmationDaily();
        entity.setUserId(userId);
        entity.setLocalDate(today);
        entity.setItemsJson(writeItems(items));
        entity.setPromptTemplateId(template.getId());
        entity.setProvider(textAiProvider.providerName());
        entity.setModel(textAiProps.model());
        return repository.save(entity);
    }

    private String writeItems(List<String> items) {
        try {
            return objectMapper.writeValueAsString(Map.of("items", items));
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "affirmation serialize failed");
        }
    }

    @SuppressWarnings("unchecked")
    private AffirmationResponse toResponse(AffirmationDaily entity) {
        List<String> items;
        try {
            // items_json 结构为 {"items": [...]}
            Map<String, Object> stored = objectMapper.readValue(entity.getItemsJson(),
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
            items = ((List<Object>) stored.getOrDefault("items", List.of()))
                    .stream().map(String::valueOf).toList();
        } catch (JacksonException e) {
            items = List.of();
        }
        return new AffirmationResponse(entity.getLocalDate().toString(), items);
    }

    private void recordCall(long userId, TextAiRequest request, long startedAt,
                            boolean success, String errorCode, String errorMessage) {
        aiCallLogService.record(new AiCallLogService.CallRecord(
                currentRequestId(), userId, null, AiCallLog.TYPE_TEXT,
                textAiProvider.providerName(), textAiProps.model(), request.operation(),
                System.currentTimeMillis() - startedAt, success, null, null,
                errorCode, errorMessage, null, null));
    }

    private String currentRequestId() {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        return requestId == null ? "" : requestId;
    }
}
