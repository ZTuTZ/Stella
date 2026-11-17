/** 状态栏高度（自定义导航下的顶部安全区），单位 px。 */
export function getStatusBarHeight(): number {
  try {
    const app = getApp<{ globalData?: { statusBarHeight?: number } }>();
    if (app && app.globalData && app.globalData.statusBarHeight) {
      return app.globalData.statusBarHeight;
    }
  } catch (e) {
    // ignore
  }
  try {
    return wx.getWindowInfo().statusBarHeight || 20;
  } catch (e) {
    return 20;
  }
}
