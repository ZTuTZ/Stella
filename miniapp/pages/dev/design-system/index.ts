/**
 * UI Phase 0 — 设计系统展示页（§42.1）。
 * 仅开发预览可见：envVersion === 'release' 时渲染空态，不入正式导航。
 * 展示所有组件状态：default / pressed / disabled / loading / long-text。
 */
Page({
  data: {
    devOnly: true,
    manifestValue: "",
  },
  onLoad() {
    try {
      const env = wx.getAccountInfoSync().miniProgram.envVersion;
      this.setData({ devOnly: env !== "release" });
    } catch (e) {
      // 拿不到环境信息时保守放行（开发工具）
      this.setData({ devOnly: true });
    }
  },
  noop() {
    // 展示页不处理业务事件
  },
  onManifestSubmit(e: WechatMiniprogram.CustomEvent<{ value: string }>) {
    this.setData({ manifestValue: e.detail.value });
  },
})
