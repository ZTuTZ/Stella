package com.futureself.admin.api;

import com.futureself.admin.application.AdminPromptService;
import com.futureself.auth.CurrentUser;
import com.futureself.common.api.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Prompt 管理（§20.3）：查看版本/新建版本/启用/禁用。同 key 仅一个 enabled（§14.4）。
 */
@RestController
@RequestMapping("/api/v1/admin")
@Validated
public class AdminPromptController {

    public record CreatePromptRequest(
            @NotBlank(message = "promptKey is required") String promptKey,
            @NotBlank(message = "systemPrompt is required") String systemPrompt,
            @NotBlank(message = "userTemplate is required") String userTemplate,
            String outputSchemaJson,
            String notes) {
    }

    private final AdminPromptService promptService;

    public AdminPromptController(AdminPromptService promptService) {
        this.promptService = promptService;
    }

    @GetMapping("/prompts")
    public ApiResponse<List<Map<String, Object>>> list(@AuthenticationPrincipal CurrentUser currentUser) {
        AdminGuard.requireAdmin(currentUser);
        return ApiResponse.ok(promptService.list());
    }

    @GetMapping("/prompts/{id}")
    public ApiResponse<Map<String, Object>> detail(@AuthenticationPrincipal CurrentUser currentUser,
                                                   @PathVariable("id") long id) {
        AdminGuard.requireAdmin(currentUser);
        return ApiResponse.ok(promptService.get(id));
    }

    @PostMapping("/prompts")
    public ApiResponse<Map<String, Object>> create(@AuthenticationPrincipal CurrentUser currentUser,
                                                   @Validated @RequestBody CreatePromptRequest request) {
        AdminGuard.requireAdmin(currentUser);
        return ApiResponse.ok(promptService.createVersion(
                request.promptKey(), request.systemPrompt(), request.userTemplate(),
                request.outputSchemaJson(), request.notes()));
    }

    @PutMapping("/prompts/{id}/enable")
    public ApiResponse<Map<String, Object>> enable(@AuthenticationPrincipal CurrentUser currentUser,
                                                   @PathVariable("id") long id) {
        AdminGuard.requireAdmin(currentUser);
        return ApiResponse.ok(promptService.enable(id));
    }

    @PutMapping("/prompts/{id}/disable")
    public ApiResponse<Map<String, Object>> disable(@AuthenticationPrincipal CurrentUser currentUser,
                                                    @PathVariable("id") long id) {
        AdminGuard.requireAdmin(currentUser);
        return ApiResponse.ok(promptService.disable(id));
    }
}
