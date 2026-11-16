package com.futureself.admin.api;

import com.futureself.admin.application.AdminDashboardService;
import com.futureself.ai.domain.AiCallLog;
import com.futureself.ai.domain.AiCallLogRepository;
import com.futureself.auth.CurrentUser;
import com.futureself.common.api.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台运维查询（§20.1/§23）：dashboard 汇总 + AI 调用日志。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminOpsController {

    private final AdminDashboardService dashboardService;
    private final AiCallLogRepository aiCallLogRepository;

    public AdminOpsController(AdminDashboardService dashboardService, AiCallLogRepository aiCallLogRepository) {
        this.dashboardService = dashboardService;
        this.aiCallLogRepository = aiCallLogRepository;
    }

    @GetMapping("/dashboard/summary")
    public ApiResponse<Map<String, Object>> summary(@AuthenticationPrincipal CurrentUser currentUser) {
        AdminGuard.requireAdmin(currentUser);
        return ApiResponse.ok(dashboardService.summary());
    }

    @GetMapping("/ai-logs")
    public ApiResponse<Page<Map<String, Object>>> aiLogs(@AuthenticationPrincipal CurrentUser currentUser,
                                                         @RequestParam(value = "requestId", required = false) String requestId,
                                                         @RequestParam(value = "operation", required = false) String operation,
                                                         @RequestParam(value = "providerType", required = false) String providerType,
                                                         @RequestParam(value = "page", defaultValue = "0") int page,
                                                         @RequestParam(value = "size", defaultValue = "20") int size) {
        AdminGuard.requireAdmin(currentUser);
        Page<AiCallLog> result = aiCallLogRepository.adminPage(
                normalize(requestId), normalize(operation), normalize(providerType),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Order.desc("createdAt"))));
        return ApiResponse.ok(result.map(this::toMap));
    }

    private String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private Map<String, Object> toMap(AiCallLog log) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", log.getId());
        m.put("requestId", log.getRequestId());
        m.put("userId", log.getUserId());
        m.put("generationTaskId", log.getGenerationTaskId());
        m.put("providerType", log.getProviderType());
        m.put("provider", log.getProvider());
        m.put("model", log.getModel());
        m.put("operation", log.getOperation());
        m.put("latencyMs", log.getLatencyMs());
        m.put("success", log.isSuccess());
        m.put("inputTokens", log.getInputTokens());
        m.put("outputTokens", log.getOutputTokens());
        m.put("errorCode", log.getErrorCode());
        m.put("errorMessage", log.getErrorMessage());
        m.put("createdAt", log.getCreatedAt() == null ? null : log.getCreatedAt().toString());
        return m;
    }
}
