/** affirmation-card — 叠卡肯定语：text 文案；footerLabel 底部提示；showActions 显隐操作钮。
 * 事件：tap / favorite（携带 favorite 新值）/ more。
 * 滑动手势（右滑收藏/左滑下一条）在页面层接入，V1 同时提供按钮兜底（§41.5.6）。 */
Component({
  properties: {
    text: { type: String, value: "我正在自然地成为我理想中的自己。" },
    footerLabel: { type: String, value: "今日肯定语" },
    showActions: { type: Boolean, value: true },
    favorite: { type: Boolean, value: false },
  },
  methods: {
    onTap() {
      this.triggerEvent("tap", {});
    },
    onFavorite() {
      this.triggerEvent("favorite", { favorite: !this.properties.favorite });
    },
    onMore() {
      this.triggerEvent("more", {});
    },
  },
})
