package com.futureself.auth.api;

import com.futureself.auth.application.WechatAuthService;
import com.futureself.common.api.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 微信登录（POST /api/v1/auth/wechat/login，实施文档 §12.1）。
 */
@RestController
@RequestMapping("/api/v1/auth")
@Validated
public class WechatAuthController {

    public record WechatLoginRequest(
            @NotBlank(message = "code is required")
            @Size(max = 256) String code) {
    }

    private final WechatAuthService wechatAuthService;

    public WechatAuthController(WechatAuthService wechatAuthService) {
        this.wechatAuthService = wechatAuthService;
    }

    @PostMapping("/wechat/login")
    public ApiResponse<Map<String, Object>> wechatLogin(@Validated @RequestBody WechatLoginRequest request) {
        WechatAuthService.LoginResult result = wechatAuthService.login(request.code());
        return ApiResponse.ok(Map.of(
                "accessToken", result.accessToken(),
                "expiresIn", result.expiresInSeconds(),
                "user", Map.of(
                        "id", result.userId(),
                        "displayName", result.displayName(),
                        "onboardingCompleted", result.onboardingCompleted())));
    }
}
