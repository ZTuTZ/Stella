/** audio-player — 播放器骨架：status ready/generating/failed；READY 态展示进度/播放/±15/环境音。
 * 事件：play（切换播放）、seek { delta }（±15）、ambience { index }、retry。
 * V1 静态骨架 + 事件占位；真实播放绑定在 UI Phase 3 接 InnerAudioContext。 */
Component({
  properties: {
    title: { type: String, value: "未来的一个普通早晨" },
    status: { type: String, value: "ready" }, // ready | generating | failed
    playing: { type: Boolean, value: false },
    progressPct: { type: Number, value: 20 },
    currentTime: { type: String, value: "00:48" },
    duration: { type: String, value: "04:12" },
    ambience: { type: Array, value: ["雨声", "咖啡馆", "海浪", "无环境音"] },
    selectedAmbience: { type: Number, value: -1 },
  },
  methods: {
    onPlay() {
      this.triggerEvent("play", { playing: !this.properties.playing });
    },
    onBack() {
      this.triggerEvent("seek", { delta: -15 });
    },
    onForward() {
      this.triggerEvent("seek", { delta: 15 });
    },
    onAmbience(e: WechatMiniprogram.TouchEvent) {
      const index = Number(e.currentTarget.dataset.index);
      this.triggerEvent("ambience", { index });
    },
    onRetry() {
      this.triggerEvent("retry", {});
    },
  },
})
