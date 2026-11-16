package com.futureself.admin.application;

import com.futureself.config.AppAdminProperties;
import com.futureself.user.UserService;
import com.futureself.user.domain.AdminUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员 bootstrap（实施文档附录 A）：
 * admin_user 表为空且配置了 ADMIN_BOOTSTRAP_PASSWORD 时创建首个管理员（BCrypt 哈希）。
 * 密码为空则跳过并 warn —— 不写死任何测试账号密码。
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserService userService;
    private final AppAdminProperties props;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AdminBootstrapRunner(UserService userService, AppAdminProperties props) {
        this.userService = userService;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (props.bootstrapPassword().isBlank()) {
            log.warn("admin bootstrap skipped: ADMIN_BOOTSTRAP_PASSWORD not set");
            return;
        }
        if (userService.findAdminByUsername(props.bootstrapUsername()).isPresent()) {
            return;
        }
        AdminUser admin = new AdminUser();
        admin.setUsername(props.bootstrapUsername());
        admin.setPasswordHash(encoder.encode(props.bootstrapPassword()));
        admin.setStatus(AdminUser.STATUS_ACTIVE);
        userService.saveAdmin(admin);
        log.info("admin bootstrap created: username={}", props.bootstrapUsername());
    }

    /** 登录校验：用户名 + BCrypt 密码 + ACTIVE 状态。 */
    public boolean verify(String username, String password) {
        return userService.findAdminByUsername(username)
                .filter(a -> AdminUser.STATUS_ACTIVE.equals(a.getStatus()))
                .filter(a -> encoder.matches(password == null ? "" : password, a.getPasswordHash()))
                .isPresent();
    }
}
