/** story-card — 故事卡：title（超长自动 2 行截断）；tone pink/lilac；showPlay/showFavorite；
 * favorite 选中态；playing 切换播放图标。事件：tap / play / favorite。 */
Component({
  properties: {
    title: { type: String, value: "未来的一个普通早晨" },
    tone: { type: String, value: "pink" },
    showPlay: { type: Boolean, value: true },
    showFavorite: { type: Boolean, value: false },
    favorite: { type: Boolean, value: false },
    playing: { type: Boolean, value: false },
  },
  methods: {
    onTap() {
      this.triggerEvent("tap", {});
    },
    onPlay() {
      this.triggerEvent("play", {});
    },
    onFavorite() {
      this.triggerEvent("favorite", { favorite: !this.properties.favorite });
    },
  },
})
