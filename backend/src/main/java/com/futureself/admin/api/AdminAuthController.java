package com.futureself.admin.api;

import com.futureself.admin.application.AdminBootstrapRunner;
import com.futureself.auth.application.TokenService;
import com.futureself.common.api.ApiResponse;
import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.user.UserService;
import com.futureself.user.domain.AdminUser;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理后台登录（§12.8/§10.10）：独立凭证，BCrypt 校验，签发 admin token。
 */
@RestController
@RequestMapping("/api/v1/admin")
@Validated
public class AdminAuthController {

    public record AdminLoginRequest(
            @NotBlank(message = "username is required") String username,
            @NotBlank(message = "password is required") String password) {
    }

    private final AdminBootstrapRunner bootstrap;
    private final UserService userService;
    private final TokenService tokenService;

    public AdminAuthController(AdminBootstrapRunner bootstrap, UserService userService, TokenService tokenService) {
        this.bootstrap = bootstrap;
        this.userService = userService;
        this.tokenService = tokenService;
    }

    @PostMapping("/auth/login")
    public ApiResponse<Map<String, Object>> login(@Validated @RequestBody AdminLoginRequest request) {
        if (!bootstrap.verify(request.username(), request.password())) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED, "用户名或密码错误");
        }
        AdminUser admin = userService.findAdminByUsername(request.username()).orElseThrow();
        userService.touchAdminLogin(admin);
        TokenService.IssuedToken token = tokenService.issueAdmin(admin.getId());
        return ApiResponse.ok(Map.of(
                "accessToken", token.accessToken(),
                "expiresIn", token.expiresInSeconds(),
                "admin", Map.of("username", admin.getUsername(), "role", "ADMIN")));
    }
}
