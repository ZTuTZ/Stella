/** mini-player — 底部迷你播放器占位（§41.5.8）：visible 控制显隐；
 * 真实播放绑定在 UI Phase 3 与全局播放状态打通。事件：play。 */
Component({
  properties: {
    visible: { type: Boolean, value: false },
    title: { type: String, value: "未来的一个普通早晨" },
    playing: { type: Boolean, value: false },
    progressPct: { type: Number, value: 0 },
    currentTime: { type: String, value: "00:00" },
    duration: { type: String, value: "00:00" },
  },
  methods: {
    onPlay() {
      this.triggerEvent("play", {});
    },
    onOpen() {
      this.triggerEvent("open", {});
    },
  },
})
