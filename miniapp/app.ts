/**
 * Future Self 小程序入口（Phase 0 骨架）。
 * Phase 2 起在 onLaunch 中处理登录态判断与恢复（实施文档 §5.1）。
 */
App({
  onLaunch() {
    // 自定义导航：记录状态栏高度，供各页面顶部安全区使用
    try {
      const info = wx.getWindowInfo();
      this.globalData.statusBarHeight = info.statusBarHeight || 20;
    } catch (e) {
      this.globalData.statusBarHeight = 20;
    }
  },
  globalData: {
    statusBarHeight: 20,
  },
})
