package com.futureself.user;

import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.user.domain.AdminUser;
import com.futureself.user.domain.AdminUserRepository;
import com.futureself.user.domain.AppUser;
import com.futureself.user.domain.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 用户领域服务：查找/创建用户、活跃时间、当前用户资料（实施文档 §9.2）。
 * 同时承载 admin_user 的查询（独立凭证体系 §10.10）。
 */
@Service
public class UserService {

    public static final String DEFAULT_DISPLAY_NAME = "微信用户";
    public static final String ROLE_USER = "USER";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    private final AppUserRepository userRepository;
    private final AdminUserRepository adminUserRepository;

    public UserService(AppUserRepository userRepository, AdminUserRepository adminUserRepository) {
        this.userRepository = userRepository;
        this.adminUserRepository = adminUserRepository;
    }

    public Optional<AppUser> findByOpenid(String openid) {
        return userRepository.findByOpenid(openid);
    }

    @Transactional
    public AppUser createUser(String openid, String unionid) {
        AppUser user = new AppUser();
        user.setOpenid(openid);
        user.setUnionid(unionid);
        user.setDisplayName(DEFAULT_DISPLAY_NAME);
        user.setRole(ROLE_USER);
        user.setStatus(STATUS_ACTIVE);
        user.setLastActiveAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    /** 按主键加载（任意状态）；鉴权过滤器用于区分“不存在”与“被禁用”。 */
    public Optional<AppUser> findById(long userId) {
        return userRepository.findById(userId);
    }

    // —— admin_user（§10.10）——

    public Optional<AdminUser> findAdminById(long adminId) {
        return adminUserRepository.findById(adminId);
    }

    public Optional<AdminUser> findAdminByUsername(String username) {
        return adminUserRepository.findByUsername(username);
    }

    @Transactional
    public AdminUser touchAdminLogin(AdminUser admin) {
        admin.setLastLoginAt(LocalDateTime.now());
        return adminUserRepository.save(admin);
    }

    @Transactional
    public AdminUser saveAdmin(AdminUser admin) {
        return adminUserRepository.save(admin);
    }

    /** 按主键加载；不存在或被禁用返回空（auth 层据此 fail-closed）。 */
    public Optional<AppUser> findActiveById(long userId) {
        return userRepository.findById(userId)
                .filter(user -> STATUS_ACTIVE.equals(user.getStatus()));
    }

    @Transactional
    public AppUser touchActive(AppUser user) {
        user.setLastActiveAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    @Transactional
    public AppUser updateDisplayName(long userId, String displayName) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND, "user not found"));
        user.setDisplayName(displayName);
        return userRepository.save(user);
    }

    @Transactional
    public AppUser completeOnboarding(long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND, "user not found"));
        user.setOnboardingCompleted(true);
        return userRepository.save(user);
    }
}
