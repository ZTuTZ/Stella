package com.futureself.auth.application;

import com.futureself.auth.infrastructure.WechatApiClient;
import com.futureself.config.AppWechatProperties;
import com.futureself.profile.ProfileService;
import com.futureself.user.UserService;
import com.futureself.user.domain.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 微信登录服务（实施文档 §18.1）：
 * code2session → openid 查/建用户（含默认 Life Profile）→ 签发 token。
 * 登录即视为活跃（last_active_at 更新）。
 * 用户被禁用（DISABLED）时由鉴权层拒绝（§22 USER_DISABLED），登录不再另行区分。
 */
@Service
public class WechatAuthService {

    private static final Logger log = LoggerFactory.getLogger(WechatAuthService.class);

    private final WechatApiClient wechatApiClient;
    private final UserService userService;
    private final ProfileService profileService;
    private final TokenService tokenService;
    private final AppWechatProperties wechatProps;

    public WechatAuthService(WechatApiClient wechatApiClient,
                             UserService userService,
                             ProfileService profileService,
                             TokenService tokenService,
                             AppWechatProperties wechatProps) {
        this.wechatApiClient = wechatApiClient;
        this.userService = userService;
        this.profileService = profileService;
        this.tokenService = tokenService;
        this.wechatProps = wechatProps;
    }

    public record LoginResult(long userId, String displayName, String accessToken, long expiresInSeconds, boolean onboardingCompleted) {
    }

    @Transactional
    public LoginResult login(String code) {
        WechatApiClient.WechatSession session = wechatApiClient.code2Session(code);

        AppUser user = userService.findByOpenid(session.openid())
                .orElseGet(() -> userService.createUser(session.openid(), session.unionid()));
        profileService.ensureProfile(user.getId());
        userService.touchActive(user);

        TokenService.IssuedToken token = tokenService.issue(user.getId());
        log.info("wechat login: userId={} mock={}", user.getId(), wechatProps.mockLogin());
        return new LoginResult(user.getId(), user.getDisplayName(), token.accessToken(), token.expiresInSeconds(), user.isOnboardingCompleted());
    }
}
