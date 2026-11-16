/**
 * launch — 启动页/登录态判断（§5.1）。
 * 有会话：校验后自动分流（onboardingCompleted → 首页，否则 onboarding）。
 * 无会话：展示欢迎封面，点「开始我的故事」触发静默登录。
 */
import { meApi } from "../../services/api";
import { clearSession, ensureLogin, getUser } from "../../stores/session";

Page({
  data: {
    loading: false,
    hasSession: false,
  },

  onLoad() {
    const user = getUser();
    if (user) {
      this.setData({ hasSession: true });
      // 已有会话：静默校验后自动分流（401 由 request client 自动重登后重试）
      meApi.me({ silent: true }).then(() => {
        this.route(user.onboardingCompleted);
      }).catch(() => {
        clearSession();
        this.setData({ hasSession: false });
      });
    }
  },

  onStart() {
    if (this.data.loading) {
      return;
    }
    this.setData({ loading: true });
    ensureLogin(true).then((user) => {
      this.setData({ loading: false, hasSession: true });
      this.route(user.onboardingCompleted);
    }).catch(() => {
      this.setData({ loading: false });
      // 失败 toast 已由 request/ensureLogin 底层统一处理
    });
  },

  route(onboardingCompleted: boolean) {
    // UI Phase 2 接入 custom-tab-bar 后改为 switchTab
    if (onboardingCompleted) {
      wx.reLaunch({ url: "/pages/home/index" });
    } else {
      wx.redirectTo({ url: "/pages/onboarding/basic/index" });
    }
  },
})
