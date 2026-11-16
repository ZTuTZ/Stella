/** brand-header — 顶部品牌区：logo 字样固定；右侧 ♡/◷ 圆形图标。
 * 事件：heart / history（由页面决定跳转，如故事库/历史）。 */
Component({
  methods: {
    onHeart() {
      this.triggerEvent("heart", {});
    },
    onHistory() {
      this.triggerEvent("history", {});
    },
  },
})
