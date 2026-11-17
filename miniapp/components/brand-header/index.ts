/** brand-header — 顶部品牌区：logo 字样固定；右侧 ♡（收藏库）。
 * showHistory 默认 false：最近播放未实现（v1.0.1 P2），不展示死入口。
 * 事件：heart / history（由页面决定跳转）。 */
Component({
  properties: {
    showHistory: { type: Boolean, value: false },
  },
  methods: {
    onHeart() {
      this.triggerEvent("heart", {});
    },
    onHistory() {
      this.triggerEvent("history", {});
    },
  },
})
