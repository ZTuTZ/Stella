/**
 * 故事详情 / 沉浸播放器（§41.6.6 / UI Phase 3）：
 * 沉浸封面（渐变 + 柔和暗化）→ 上叠播放器卡（全局 InnerAudioContext 真实播放）→ 全文展开。
 * 音频未 READY 时保持完整高级阅读态（§42.4），并低频轮询音频状态；
 * FAILED 可单独重试音频（§20.4）；文本随时可读（§32.6）。
 */
import { API_BASE_URL } from "../../../services/config";
import { storyApi } from "../../../services/api";
import { StoryResponse } from "../../../types/api";
import {
  formatTime,
  getPlayerState,
  loadAudio,
  playerProgressPct,
  seekBy,
  subscribePlayer,
  togglePlay,
} from "../../../stores/player";

let pollTimer = 0;
let pollStopped = false;
let unsubscribe: (() => void) | null = null;

Page({
  data: {
    storyId: 0,
    story: null as StoryResponse | null,
    paragraphs: [] as string[],
    previewParagraphs: [] as string[],
    contentExpanded: false,
    loading: true,
    // 播放器状态（来自全局 store）
    playerStatus: "generating", // ready | generating | failed
    playerPlaying: false,
    playerProgressPct: 0,
    playerCurrentTime: "00:00",
    playerDuration: "00:00",
    selectedAmbience: -1,
  },

  onLoad(options: Record<string, string | undefined>) {
    this.setData({ storyId: Number(options.storyId || 0) });
    this.fetch();
    unsubscribe = subscribePlayer(() => this.syncPlayerState());
  },

  onUnload() {
    pollStopped = true;
    if (pollTimer) {
      clearTimeout(pollTimer);
      pollTimer = 0;
    }
    if (unsubscribe) {
      unsubscribe();
      unsubscribe = null;
    }
  },

  onHide() {
    pollStopped = true;
    if (pollTimer) {
      clearTimeout(pollTimer);
      pollTimer = 0;
    }
  },

  onShow() {
    if (this.data.story && !this.audioReady() && pollStopped) {
      pollStopped = false;
      this.schedulePoll(0);
    }
  },

  fetch() {
    storyApi.detail(this.data.storyId).then((story) => {
      const paragraphs = (story.content || "")
        .split(/\n+/)
        .map((p) => p.trim())
        .filter((p) => p);
      this.setData({
        story,
        paragraphs,
        previewParagraphs: paragraphs.slice(0, 2),
        loading: false,
      });
      this.applyAudio(story);
    }).catch(() => {
      this.setData({ loading: false });
    });
  },

  audioReady(): boolean {
    const s = this.data.story;
    return !!(s && s.audio && s.audio.status === "READY" && s.audio.url);
  },

  /** 音频状态流转：READY → 载入全局播放器；未就绪 → 轮询；FAILED → 可重试。 */
  applyAudio(story: StoryResponse) {
    if (story.audio && story.audio.status === "READY" && story.audio.url) {
      pollStopped = true;
      if (pollTimer) {
        clearTimeout(pollTimer);
        pollTimer = 0;
      }
      const url = story.audio.url.startsWith("http") ? story.audio.url : API_BASE_URL + story.audio.url;
      loadAudio(story.id, story.title, url);
      this.syncPlayerState("ready");
      return;
    }
    if (story.audio && story.audio.status === "FAILED") {
      pollStopped = true;
      this.setData({ playerStatus: "failed" });
      return;
    }
    // PENDING/SUBMITTED/PROCESSING/无 asset：继续轮询
    this.setData({ playerStatus: "generating" });
    if (!pollStopped) {
      this.schedulePoll(4000);
    }
  },

  schedulePoll(delayMs: number) {
    if (pollTimer) {
      clearTimeout(pollTimer);
    }
    pollTimer = setTimeout(() => {
      if (pollStopped) {
        return;
      }
      storyApi.detail(this.data.storyId).then((story) => {
        if (pollStopped) {
          return;
        }
        this.setData({ story });
        this.applyAudio(story);
      }).catch(() => {
        this.schedulePoll(4000);
      });
    }, delayMs) as unknown as number;
  },

  syncPlayerState(statusOverride?: string) {
    const s = getPlayerState();
    this.setData({
      playerPlaying: s.playing,
      playerProgressPct: playerProgressPct(),
      playerCurrentTime: formatTime(s.currentTimeSec),
      playerDuration: formatTime(s.durationSec),
      playerStatus: statusOverride || this.data.playerStatus,
    });
  },

  // —— 播放器事件 ——
  onPlayTap() {
    if (!this.audioReady()) {
      return;
    }
    const story = this.data.story as StoryResponse;
    const url = (story.audio && story.audio.url) || "";
    const fullUrl = url.startsWith("http") ? url : API_BASE_URL + url;
    loadAudio(story.id, story.title, fullUrl);
    togglePlay();
  },

  onSeek(e: WechatMiniprogram.CustomEvent<{ delta: number }>) {
    seekBy(e.detail.delta);
  },

  onAmbience(e: WechatMiniprogram.CustomEvent<{ index: number }>) {
    // V1 Mock 无环境音混合能力，仅记录选择（真实 Provider 接入后生效）
    this.setData({ selectedAmbience: e.detail.index });
    wx.showToast({ title: "环境音将在真实音频接入后生效", icon: "none", duration: 1600 });
  },

  onRetryAudio() {
    storyApi.retryAudio(this.data.storyId).then(() => {
      wx.showToast({ title: "已重新提交生成", icon: "none" });
      this.setData({ playerStatus: "generating" });
      pollStopped = false;
      this.schedulePoll(3000);
    }).catch(() => {
      // toast 已由底层处理
    });
  },

  // —— 收藏 / 更多 / 全文 ——
  onFavorite() {
    const story = this.data.story;
    if (!story) {
      return;
    }
    const next = !story.favorite;
    storyApi.setFavorite(story.id, next).then(() => {
      this.setData({ "story.favorite": next });
      wx.showToast({ title: next ? "已收藏" : "已取消收藏", icon: "none", duration: 1200 });
    }).catch(() => {
      // toast 已由底层处理
    });
  },

  onMore() {
    wx.showToast({ title: "更多操作即将上线", icon: "none", duration: 1400 });
  },

  toggleContent() {
    this.setData({ contentExpanded: !this.data.contentExpanded });
  },

  goBack() {
    wx.navigateBack();
  },
})
