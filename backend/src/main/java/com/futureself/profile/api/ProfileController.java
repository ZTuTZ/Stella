package com.futureself.profile.api;

import com.futureself.auth.CurrentUser;
import com.futureself.common.api.ApiResponse;
import com.futureself.profile.ProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Life Profile 接口（实施文档 §12.2）。
 * 仅操作 token 对应用户自己的档案；每次 PUT 版本 +1。
 */
@RestController
@RequestMapping("/api/v1/me")
@Validated
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/life-profile")
    public ApiResponse<LifeProfileResponse> getProfile(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(profileService.getProfile(currentUser.userId()));
    }

    @PutMapping("/life-profile")
    public ApiResponse<LifeProfileResponse> updateProfile(@AuthenticationPrincipal CurrentUser currentUser,
                                                          @Validated @RequestBody LifeProfileUpdateRequest request) {
        return ApiResponse.ok(profileService.updateProfile(currentUser.userId(), request));
    }
}
