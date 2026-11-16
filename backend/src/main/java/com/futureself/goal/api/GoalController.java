package com.futureself.goal.api;

import com.futureself.auth.CurrentUser;
import com.futureself.common.api.ApiResponse;
import com.futureself.generation.GenerationTaskService;
import com.futureself.goal.FollowupService;
import com.futureself.goal.GoalService;
import com.futureself.user.UserEventService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Goal 接口（实施文档 §12.3）。
 * 所有按 id 的访问均校验 owner（§3.1），越权一律 404，不泄露存在性。
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class GoalController {

    private final GoalService goalService;
    private final FollowupService followupService;
    private final GenerationTaskService taskService;
    private final UserEventService userEventService;

    public GoalController(GoalService goalService, FollowupService followupService,
                          GenerationTaskService taskService, UserEventService userEventService) {
        this.goalService = goalService;
        this.followupService = followupService;
        this.taskService = taskService;
        this.userEventService = userEventService;
    }

    @PostMapping("/goals")
    public ApiResponse<com.futureself.goal.api.GoalResponse> create(@AuthenticationPrincipal CurrentUser currentUser,
                                                                    @Validated @RequestBody CreateGoalRequest request) {
        com.futureself.goal.api.GoalResponse created = goalService.create(currentUser.userId(), request);
        userEventService.record(currentUser.userId(), UserEventService.EVENT_GOAL_CREATED, "GOAL", created.id(), null);
        return ApiResponse.ok(created);
    }

    @GetMapping("/goals")
    public ApiResponse<List<com.futureself.goal.api.GoalResponse>> list(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(goalService.listOwn(currentUser.userId()));
    }

    @GetMapping("/goals/{id}")
    public ApiResponse<com.futureself.goal.api.GoalResponse> get(@AuthenticationPrincipal CurrentUser currentUser,
                                                                 @PathVariable("id") long id) {
        return ApiResponse.ok(goalService.getOwn(id, currentUser.userId()));
    }

    @PatchMapping("/goals/{id}")
    public ApiResponse<com.futureself.goal.api.GoalResponse> update(@AuthenticationPrincipal CurrentUser currentUser,
                                                                    @PathVariable("id") long id,
                                                                    @Validated @RequestBody UpdateGoalRequest request) {
        return ApiResponse.ok(goalService.updateOwn(id, currentUser.userId(), request));
    }

    @DeleteMapping("/goals/{id}")
    public ApiResponse<Map<String, Object>> delete(@AuthenticationPrincipal CurrentUser currentUser,
                                                   @PathVariable("id") long id) {
        goalService.deleteOwn(id, currentUser.userId());
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @PostMapping("/goals/{id}/followup-questions")
    public ApiResponse<FollowupQuestionsResponse> followupQuestions(@AuthenticationPrincipal CurrentUser currentUser,
                                                                    @PathVariable("id") long id) {
        return ApiResponse.ok(followupService.generateQuestions(id, currentUser.userId()));
    }

    @PutMapping("/goals/{id}/followup-answers")
    public ApiResponse<com.futureself.goal.api.GoalResponse> followupAnswers(@AuthenticationPrincipal CurrentUser currentUser,
                                                                             @PathVariable("id") long id,
                                                                             @Validated @RequestBody FollowupAnswersRequest request) {
        return ApiResponse.ok(followupService.saveAnswers(id, currentUser.userId(), request));
    }

    /** 发起故事生成（§12.4）。支持 Idempotency-Key 幂等（§24）。 */
    @PostMapping("/goals/{id}/generate-story")
    public ApiResponse<Map<String, Object>> generateStory(@AuthenticationPrincipal CurrentUser currentUser,
                                                          @PathVariable("id") long id,
                                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        GenerationTaskService.CreatedTask created = taskService.create(currentUser.userId(), id, idempotencyKey);
        userEventService.record(currentUser.userId(), UserEventService.EVENT_STORY_REQUESTED, "GOAL", id, null);
        return ApiResponse.ok(Map.of("taskId", created.taskId(), "status", created.status()));
    }
}
