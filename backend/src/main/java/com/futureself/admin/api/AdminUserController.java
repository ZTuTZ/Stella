package com.futureself.admin.api;

import com.futureself.admin.application.AdminUserService;
import com.futureself.auth.CurrentUser;
import com.futureself.common.api.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台用户管理（§20.2）：列表/详情/封禁。Life Profile 展示需标注仅供排查。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/users")
    public ApiResponse<Page<AdminUserService.UserBrief>> users(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        AdminGuard.requireAdmin(currentUser);
        return ApiResponse.ok(adminUserService.pageUsers(keyword, page, size));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<Map<String, Object>> detail(@AuthenticationPrincipal CurrentUser currentUser,
                                                   @PathVariable("id") long id) {
        AdminGuard.requireAdmin(currentUser);
        return ApiResponse.ok(adminUserService.detail(id));
    }

    @PutMapping("/users/{id}/status")
    public ApiResponse<Map<String, Object>> updateStatus(@AuthenticationPrincipal CurrentUser currentUser,
                                                         @PathVariable("id") long id,
                                                         @RequestBody Map<String, String> body) {
        AdminGuard.requireAdmin(currentUser);
        return ApiResponse.ok(adminUserService.updateStatus(id, body.getOrDefault("status", "")));
    }
}
