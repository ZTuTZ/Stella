/** custom-tab-bar — 首页/我的（§41.9）。各 tab 页在 onShow 中 setSelected(index)。 */
Component({
  data: {
    selected: 0,
    list: [
      { pagePath: "/pages/home/index", text: "首页" },
      { pagePath: "/pages/profile/index", text: "我的" },
    ],
  },
  methods: {
    onSwitch(e: WechatMiniprogram.TouchEvent) {
      const path = String(e.currentTarget.dataset.path);
      wx.switchTab({ url: path });
    },
  },
})
