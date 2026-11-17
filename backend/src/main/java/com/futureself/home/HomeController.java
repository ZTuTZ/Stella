package com.futureself.home;

import com.futureself.affirmation.AffirmationService;
import com.futureself.auth.CurrentUser;
import com.futureself.common.api.ApiResponse;
import com.futureself.common.time.BusinessTime;
import com.futureself.generation.domain.GenerationTask;
import com.futureself.generation.domain.GenerationTaskRepository;
import com.futureself.home.api.HomeResponse;
import com.futureself.story.StoryService;
import com.futureself.story.domain.Story;
import com.futureself.story.domain.StoryRepository;
import com.futureself.user.UserService;
import com.futureself.user.domain.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * 首页聚合（GET /api/v1/home，实施文档 §12.7/§5.3）。
 * 一个接口返回首页主数据；主卡优先“今日生成的故事”，否则最近一条。
 */
@RestController
@RequestMapping("/api/v1")
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    private final UserService userService;
    private final StoryService storyService;
    private final StoryRepository storyRepository;
    private final AffirmationService affirmationService;
    private final GenerationTaskRepository taskRepository;

    public HomeController(UserService userService,
                          StoryService storyService,
                          StoryRepository storyRepository,
                          AffirmationService affirmationService,
                          GenerationTaskRepository taskRepository) {
        this.userService = userService;
        this.storyService = storyService;
        this.storyRepository = storyRepository;
        this.affirmationService = affirmationService;
        this.taskRepository = taskRepository;
    }

    @GetMapping("/home")
    public ApiResponse<HomeResponse> home(@AuthenticationPrincipal CurrentUser currentUser) {
        AppUser user = userService.findById(currentUser.userId())
                .orElseThrow(() -> new IllegalStateException("authenticated user missing"));

        var today = BusinessTime.today();
        Optional<Story> todayStory = storyRepository
                .findTopByUserIdAndStatusAndModerationStatusAndCreatedAtBetweenOrderByCreatedAtDesc(
                        user.getId(), Story.STATUS_ACTIVE, Story.MODERATION_PASSED,
                        today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        if (todayStory.isEmpty()) {
            todayStory = storyRepository.findTopByUserIdAndStatusAndModerationStatusOrderByCreatedAtDesc(
                    user.getId(), Story.STATUS_ACTIVE, Story.MODERATION_PASSED);
        }
        List<Story> recent = storyRepository.findTop3ByUserIdAndStatusAndModerationStatusOrderByCreatedAtDesc(
                user.getId(), Story.STATUS_ACTIVE, Story.MODERATION_PASSED);

        // 肯定句生成失败不拖垮首页（可用 /affirmations/today 单独重试）：
        // 失败时返回空的 AffirmationBrief（date=业务时区今天，items=[]），绝不返回 null。
        HomeResponse.AffirmationBrief affirmations;
        try {
            var a = affirmationService.today(user.getId());
            affirmations = new HomeResponse.AffirmationBrief(a.date(), a.items());
        } catch (RuntimeException e) {
            // 只记录异常类型，不记录用户 prompt/正文（H-07 隐私要求）
            log.warn("home affirmation fallback: type={}", e.getClass().getSimpleName());
            affirmations = new HomeResponse.AffirmationBrief(BusinessTime.today().toString(), List.of());
        }
        boolean hasRunningTask = taskRepository.existsByUserIdAndStatusNotIn(
                user.getId(), List.of(
                        GenerationTask.STATUS_COMPLETED, GenerationTask.STATUS_FAILED, GenerationTask.STATUS_BLOCKED));

        return ApiResponse.ok(new HomeResponse(
                new HomeResponse.UserBrief(user.getId(), user.getDisplayName()),
                todayStory.map(storyService::toResponse).orElse(null),
                recent.stream().map(storyService::toResponse).toList(),
                affirmations,
                hasRunningTask));
    }
}
