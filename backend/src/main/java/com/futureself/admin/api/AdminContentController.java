package com.futureself.admin.api;

import com.futureself.admin.application.AdminTaskService;
import com.futureself.admin.application.AdminUserService;
import com.futureself.auth.CurrentUser;
import com.futureself.common.api.ApiResponse;
import com.futureself.generation.domain.GenerationTask;
import com.futureself.generation.domain.GenerationTaskRepository;
import com.futureself.goal.domain.GoalRepository;
import com.futureself.goal.domain.ManifestationGoal;
import com.futureself.story.domain.Story;
import com.futureself.story.domain.StoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后台内容管理（§20.2/§20.4）：goals/stories/tasks 列表 + 任务手动重试。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminContentController {

    private final GoalRepository goalRepository;
    private final StoryRepository storyRepository;
    private final GenerationTaskRepository taskRepository;
    private final AdminTaskService adminTaskService;
    private final AdminUserService adminUserService;

    public AdminContentController(GoalRepository goalRepository,
                                  StoryRepository storyRepository,
                                  GenerationTaskRepository taskRepository,
                                  AdminTaskService adminTaskService,
                                  AdminUserService adminUserService) {
        this.goalRepository = goalRepository;
        this.storyRepository = storyRepository;
        this.taskRepository = taskRepository;
        this.adminTaskService = adminTaskService;
        this.adminUserService = adminUserService;
    }

    @GetMapping("/goals")
    public ApiResponse<Page<Map<String, Object>>> goals(@AuthenticationPrincipal CurrentUser currentUser,
                                                        @RequestParam(value = "userId", required = false) Long userId,
                                                        @RequestParam(value = "status", required = false) String status,
                                                        @RequestParam(value = "page", defaultValue = "0") int page,
                                                        @RequestParam(value = "size", defaultValue = "20") int size) {
        AdminGuard.requireAdmin(currentUser);
        Page<ManifestationGoal> result = goalRepository.adminPage(userId, normalize(status), pageable(page, size));
        return ApiResponse.ok(result.map(this::goalToMap));
    }

    @GetMapping("/stories")
    public ApiResponse<Page<Map<String, Object>>> stories(@AuthenticationPrincipal CurrentUser currentUser,
                                                          @RequestParam(value = "userId", required = false) Long userId,
                                                          @RequestParam(value = "status", required = false) String status,
                                                          @RequestParam(value = "page", defaultValue = "0") int page,
                                                          @RequestParam(value = "size", defaultValue = "20") int size) {
        AdminGuard.requireAdmin(currentUser);
        Page<Story> result = storyRepository.adminPage(userId, normalize(status), pageable(page, size));
        return ApiResponse.ok(result.map(this::storyToMap));
    }

    @GetMapping("/tasks")
    public ApiResponse<Page<Map<String, Object>>> tasks(@AuthenticationPrincipal CurrentUser currentUser,
                                                        @RequestParam(value = "userId", required = false) Long userId,
                                                        @RequestParam(value = "status", required = false) String status,
                                                        @RequestParam(value = "page", defaultValue = "0") int page,
                                                        @RequestParam(value = "size", defaultValue = "20") int size) {
        AdminGuard.requireAdmin(currentUser);
        Page<GenerationTask> result = taskRepository.adminPage(userId, normalize(status), pageable(page, size));
        return ApiResponse.ok(result.map(this::taskToMap));
    }

    /** 手动重试（§20.4）：音频失败只重试音频；文本失败重置回生成阶段。 */
    @PostMapping("/tasks/{id}/retry")
    public ApiResponse<Map<String, Object>> retryTask(@AuthenticationPrincipal CurrentUser currentUser,
                                                      @PathVariable("id") long id) {
        AdminGuard.requireAdmin(currentUser);
        return ApiResponse.ok(adminTaskService.retry(id));
    }

    private PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.desc("createdAt")));
    }

    private String normalize(String status) {
        return (status == null || status.isBlank()) ? null : status.trim();
    }

    private Map<String, Object> goalToMap(ManifestationGoal goal) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", goal.getId());
        m.put("userId", goal.getUserId());
        m.put("category", goal.getCategory());
        m.put("title", goal.getTitle());
        m.put("targetTimeText", goal.getTargetTimeText());
        m.put("status", goal.getStatus());
        m.put("createdAt", goal.getCreatedAt() == null ? null : goal.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> storyToMap(Story story) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", story.getId());
        m.put("userId", story.getUserId());
        m.put("goalId", story.getGoalId());
        m.put("title", story.getTitle());
        m.put("status", story.getStatus());
        m.put("moderationStatus", story.getModerationStatus());
        m.put("provider", story.getProvider());
        m.put("model", story.getModel());
        m.put("promptTemplateId", story.getPromptTemplateId());
        m.put("profileVersion", story.getProfileVersion());
        m.put("createdAt", story.getCreatedAt() == null ? null : story.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> taskToMap(GenerationTask task) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", task.getId());
        m.put("userId", task.getUserId());
        m.put("goalId", task.getGoalId());
        m.put("storyId", task.getStoryId());
        m.put("status", task.getStatus());
        m.put("progress", task.getProgress());
        m.put("retryCount", task.getRetryCount());
        m.put("errorStage", task.getErrorStage());
        m.put("errorCode", task.getErrorCode());
        m.put("errorMessage", task.getErrorMessage());
        m.put("createdAt", task.getCreatedAt() == null ? null : task.getCreatedAt().toString());
        m.put("finishedAt", task.getFinishedAt() == null ? null : task.getFinishedAt().toString());
        return m;
    }
}
