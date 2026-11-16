package com.futureself.config;

import tools.jackson.databind.ObjectMapper;
import com.futureself.auth.TokenAuthFilter;
import com.futureself.auth.application.TokenService;
import com.futureself.common.api.ApiResponse;
import com.futureself.common.error.ErrorCode;
import com.futureself.user.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

/**
 * 无状态 token 安全配置（实施文档 §6.1/§9.1）。
 * 放行：登录、健康检查（本地/内网，§23）、受控媒体路由（音频播放，§16.2）。
 * 其余接口需有效 Bearer token。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   TokenService tokenService,
                                                   UserService userService,
                                                   ObjectMapper objectMapper) throws Exception {
        TokenAuthFilter tokenAuthFilter = new TokenAuthFilter(tokenService, userService, objectMapper);

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api/v1/admin/auth/login",
                                "/actuator/**",
                                "/media/**",
                                "/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.getWriter().write(objectMapper.writeValueAsString(
                                    ApiResponse.error(ErrorCode.AUTH_REQUIRED, "authentication required")));
                        }))
                .addFilterBefore(tokenAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
