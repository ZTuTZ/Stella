package com.futureself.auth;

/**
 * 当前登录用户上下文（由 TokenAuthFilter 从 token 解析并载入 SecurityContext）。
 */
public record CurrentUser(Long userId, String displayName, String role, String status) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
