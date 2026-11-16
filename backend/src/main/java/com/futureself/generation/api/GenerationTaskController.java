package com.futureself.generation.api;

import com.futureself.auth.CurrentUser;
import com.futureself.common.api.ApiResponse;
import com.futureself.generation.GenerationTaskService;
import com.futureself.generation.domain.GenerationTask;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 任务状态查询（实施文档 §12.4）。owner 校验：他人任务一律 404。
 */
@RestController
@RequestMapping("/api/v1")
public class GenerationTaskController {

    private final GenerationTaskService taskService;

    public GenerationTaskController(GenerationTaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/generation-tasks/{id}")
    public ApiResponse<Map<String, Object>> status(@AuthenticationPrincipal CurrentUser currentUser,
                                                   @PathVariable("id") long id) {
        GenerationTask task = taskService.loadOwnTask(id, currentUser.userId());
        return ApiResponse.ok(taskService.toResponse(task));
    }
}
