package com.futureself.auth;

import tools.jackson.databind.ObjectMapper;
import com.futureself.auth.application.TokenService;
import com.futureself.common.api.ApiResponse;
import com.futureself.common.error.ErrorCode;
import com.futureself.user.UserService;
import com.futureself.user.domain.AdminUser;
import com.futureself.user.domain.AppUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Bearer token 鉴权过滤器（实施文档 §9.1/§23）。
 * 有效 token + ACTIVE 用户 → 写入 SecurityContext（principal=CurrentUser）；
 * 无/无效 token → 401 AUTH_REQUIRED；
 * token 有效但用户被禁用 → 403 USER_DISABLED。
 * 日志禁止输出完整 token；Redis 不可用时 fail-closed（返回 401）。
 */
public class TokenAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TokenAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public TokenAuthFilter(TokenService tokenService, UserService userService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            Optional<String> raw = tokenService.resolveRaw(token);

            if (raw.isEmpty()) {
                log.debug("auth rejected: invalid or expired token");
                writeError(response, HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REQUIRED, "invalid or expired token");
                return;
            }

            // 管理后台 token（admin:{id}，§10.10 独立凭证）
            if (raw.get().startsWith("admin:")) {
                Optional<Long> adminId = tokenService.resolveAdminId(token);
                Optional<AdminUser> admin = adminId.flatMap(userService::findAdminById);
                if (admin.isEmpty()) {
                    log.debug("auth rejected: admin not found, adminId={}", adminId);
                    writeError(response, HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REQUIRED, "invalid or expired token");
                    return;
                }
                AdminUser a = admin.get();
                CurrentUser principal = new CurrentUser(a.getId(), a.getUsername(), "ADMIN", a.getStatus());
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, List.of());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                chain.doFilter(request, response);
                return;
            }

            Optional<Long> userId = tokenService.resolveUserId(token);

            if (userId.isEmpty()) {
                log.debug("auth rejected: invalid or expired token");
                writeError(response, HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REQUIRED, "invalid or expired token");
                return;
            }

            Optional<AppUser> user = userService.findById(userId.get());
            if (user.isEmpty()) {
                log.debug("auth rejected: user not found, userId={}", userId.get());
                writeError(response, HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REQUIRED, "invalid or expired token");
                return;
            }
            if (!UserService.STATUS_ACTIVE.equals(user.get().getStatus())) {
                log.debug("auth rejected: user disabled, userId={}", userId.get());
                writeError(response, HttpStatus.FORBIDDEN, ErrorCode.USER_DISABLED, "user is disabled");
                return;
            }

            AppUser appUser = user.get();
            CurrentUser principal = new CurrentUser(
                    appUser.getId(), appUser.getDisplayName(), appUser.getRole(), appUser.getStatus());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, HttpStatus status, ErrorCode code, String message)
            throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(code, message)));
    }
}
