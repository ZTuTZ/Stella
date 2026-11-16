package com.futureself.user.api;

import com.futureself.auth.CurrentUser;
import com.futureself.common.api.ApiResponse;
import com.futureself.user.UserService;
import com.futureself.user.domain.AppUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户接口（实施文档 §12.2）。
 * 所有数据都基于 token 解析出的当前用户，不存在跨用户寻址入口。
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal CurrentUser currentUser) {
        AppUser user = userService.findById(currentUser.userId())
                .orElseThrow(() -> new IllegalStateException("authenticated user missing"));
        return ApiResponse.ok(new MeResponse(user.getId(), user.getDisplayName(), user.isOnboardingCompleted()));
    }

    @PatchMapping("/me")
    public ApiResponse<MeResponse> updateMe(@AuthenticationPrincipal CurrentUser currentUser,
                                            @Validated @RequestBody UpdateMeRequest request) {
        AppUser user = userService.updateDisplayName(currentUser.userId(), request.displayName());
        return ApiResponse.ok(new MeResponse(user.getId(), user.getDisplayName(), user.isOnboardingCompleted()));
    }

    @PostMapping("/me/onboarding/complete")
    public ApiResponse<MeResponse> completeOnboarding(@AuthenticationPrincipal CurrentUser currentUser) {
        AppUser user = userService.completeOnboarding(currentUser.userId());
        return ApiResponse.ok(new MeResponse(user.getId(), user.getDisplayName(), user.isOnboardingCompleted()));
    }
}
