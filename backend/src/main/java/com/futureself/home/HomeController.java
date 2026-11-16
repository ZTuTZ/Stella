package com.futureself.home;

import com.futureself.affirmation.AffirmationService;
import com.futureself.auth.CurrentUser;
import com.futureself.common.api.ApiResponse;
import com.futureself.generation.domain.GenerationTask;
import com.futureself.generation.domain.GenerationTaskRepository;
import com.futureself.home.api.HomeResponse;
import com.futureself.story.StoryService;
import com.futureself.story.domain.Story;
import com.futureself.story.domain.StoryRepository;
import com.futureself.user.UserService;
import com.futureself.user.domain.AppUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 首页聚合（GET /api/v1/home，实施文档 §12.7/§5.3）。
 * 一个接口返回首页主数据；主卡优先“今日生成的故事”，否则最近一条。
 */
@RestController
@RequestMapping("/api/v1")
public class HomeController {

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

        Optional<Story> todayStory = storyRepository
                .findTopByUserIdAndStatusAndModerationStatusAndCreatedAtBetweenOrderByCreatedAtDesc(
                        user.getId(), Story.STATUS_ACTIVE, Story.MODERATION_PASSED,
                        LocalDate.now().atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay());
        if (todayStory.isEmpty()) {
            todayStory = storyRepository.findTopByUserIdAndStatusAndModerationStatusOrderByCreatedAtDesc(
                    user.getId(), Story.STATUS_ACTIVE, Story.MODERATION_PASSED);
        }
        List<Story> recent = storyRepository.findTop3ByUserIdAndStatusAndModerationStatusOrderByCreatedAtDesc(
                user.getId(), Story.STATUS_ACTIVE, Story.MODERATION_PASSED);

        // 肯定句生成失败不拖垮首页（可用 /affirmations/today 单独重试）
        HomeResponse.AffirmationBrief affirmations;
        try {
            var a = affirmationService.today(user.getId());
            affirmations = new HomeResponse.AffirmationBrief(a.date(), a.items());
        } catch (RuntimeException e) {
            affirmations = null;
        }
        boolean hasRunningTask = taskRepository.existsByUserIdAndStatusNotIn(
                user.getId(), List.of(
                        GenerationTask.STATUS_COMPLETED, GenerationTask.STATUS_FAILED, GenerationTask.STATUS_BLOCKED));

        return ApiResponse.ok(new HomeResponse(
                new HomeResponse.UserBrief(user.getId(), user.getDisplayName()),
                todayStory.map(storyService::toResponse).orElse(null),
                recent.stream().map(storyService::toResponse).toList(),
                new HomeResponse.AffirmationBrief(affirmations.date(), affirmations.items()),
                hasRunningTask));
    }
}
