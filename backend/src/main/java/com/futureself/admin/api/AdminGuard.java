package com.futureself.admin.api;

import com.futureself.auth.CurrentUser;
import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;

/**
 * Admin 接口守卫（§3.2）：仅 ADMIN 角色可访问 /admin/** 业务接口。
 */
public final class AdminGuard {

    private AdminGuard() {
    }

    public static void requireAdmin(CurrentUser currentUser) {
        if (currentUser == null || !currentUser.isAdmin()) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "admin permission required");
        }
    }
}
