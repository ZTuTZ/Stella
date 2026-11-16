/**
 * 首页（§41.6.3 / UI Phase 2）：
 * 问候 + manifest 输入（提交→快捷创建愿望）+ 热门愿望 chips（填充不提交）+
 * For you 故事轮播（/home 数据）+ Today's affirmation 叠卡（滑动换条/收藏视觉态）。
 * 数据源：GET /api/v1/home 一个接口（§12.7）。
 */
import { homeApi } from "../../services/api";
import { CarouselStory, HomeResponse } from "../../types/api";
import {
  formatTime,
  getPlayerState,
  playerProgressPct,
  subscribePlayer,
  togglePlay,
} from "../../stores/player";
import { PlayerState } from "../../stores/player";

const TRENDING = ["事业更自由", "理想爱情", "旅行", "更自信", "学会放松"];

Page({
  data: {
    greeting: "你想实现什么？",
    manifestValue: "",
    trending: TRENDING,
    stories: [] as CarouselStory[],
    affirmationText: "",
    affirmationFavorite: false,
    hasRunningTask: false,
    // Mini Player（与全局播放器同步 §42.4）
    miniPlayerVisible: false,
    miniPlayerTitle: "",
    miniPlayerPlaying: false,
    miniPlayerProgressPct: 0,
    miniPlayerCurrentTime: "00:00",
    miniPlayerDuration: "00:00",
  },

  affItems: [] as string[],
  affIndex: 0,
  affTouchStartX: 0,
  unsubscribePlayer: null as null | (() => void),

  onLoad() {
    this.unsubscribePlayer = subscribePlayer(() => this.syncMiniPlayer());
  },

  onUnload() {
    if (this.unsubscribePlayer) {
      this.unsubscribePlayer();
      this.unsubscribePlayer = null;
    }
  },

  onShow() {
    this.setSelectedTab(0);
    this.refresh();
    this.syncMiniPlayer();
  },

  setSelectedTab(index: number) {
    const tabBar = this.getTabBar();
    if (tabBar) {
      tabBar.setData({ selected: index });
    }
  },

  /** Mini Player：离开故事详情但音频仍在播放时显示（§41.5.8） */
  syncMiniPlayer() {
    const s: PlayerState = getPlayerState();
    this.setData({
      miniPlayerVisible: s.loaded,
      miniPlayerTitle: s.title,
      miniPlayerPlaying: s.playing,
      miniPlayerProgressPct: playerProgressPct(),
      miniPlayerCurrentTime: formatTime(s.currentTimeSec),
      miniPlayerDuration: formatTime(s.durationSec),
    });
  },

  onMiniPlay() {
    togglePlay();
  },

  onMiniOpen() {
    const s = getPlayerState();
    if (s.storyId) {
      wx.navigateTo({ url: "/pages/story/detail/index?storyId=" + s.storyId });
    }
  },

  refresh() {
    homeApi.home().then((data: HomeResponse) => {
      const name = data.user.displayName || "";
      const stories: CarouselStory[] = [];
      if (data.todayStory) {
        stories.push({
          id: data.todayStory.id,
          title: data.todayStory.title,
          tone: "pink",
          favorite: data.todayStory.favorite,
          playing: false,
        });
      }
      data.recentStories.forEach((s, i) => {
        if (data.todayStory && s.id === data.todayStory.id) {
          return;
        }
        stories.push({ id: s.id, title: s.title, tone: i % 2 === 0 ? "lilac" : "pink", favorite: s.favorite, playing: false });
      });
      this.affItems = data.affirmations ? data.affirmations.items : [];
      this.affIndex = 0;
      this.setData({
        greeting: name ? `${name}，你想实现什么？` : "你想实现什么？",
        stories,
        hasRunningTask: data.hasRunningTask,
        affirmationText: this.affItems.length > 0 ? this.affItems[0] : "",
        affirmationFavorite: false,
      });
    }).catch(() => {
      // 错误 toast 已由 request client 统一处理
    });
  },

  // —— manifest 输入 / 热门愿望 ——
  onManifestChange(e: WechatMiniprogram.CustomEvent<{ value: string }>) {
    this.setData({ manifestValue: e.detail.value });
  },

  onManifestSubmit(e: WechatMiniprogram.CustomEvent<{ value: string }>) {
    const value = e.detail.value.trim();
    if (!value) {
      return;
    }
    // 快捷创建：复用 goal 页 standalone 模式（标题预填）
    wx.navigateTo({
      url: "/pages/onboarding/goal/index?standalone=1&prefill=" + encodeURIComponent(value),
    });
  },

  onTrendingTap(e: WechatMiniprogram.TouchEvent) {
    // 点击填充到主输入框，不立即提交（§41.5.3）
    const label = String(e.currentTarget.dataset.label || "");
    this.setData({ manifestValue: label });
  },

  // —— For You ——
  onStoryTap(e: WechatMiniprogram.CustomEvent<{ id: number }>) {
    wx.navigateTo({ url: "/pages/story/detail/index?storyId=" + e.detail.id });
  },

  onViewAll() {
    wx.navigateTo({ url: "/pages/story/list/index" });
  },

  // —— Affirmation 叠卡（右滑收藏 / 左滑下一条，按钮兜底）——
  onAffTouchStart(e: WechatMiniprogram.TouchEvent) {
    this.affTouchStartX = e.touches[0].clientX;
  },

  onAffTouchEnd(e: WechatMiniprogram.TouchEvent) {
    const delta = e.changedTouches[0].clientX - this.affTouchStartX;
    if (delta > 60) {
      this.setData({ affirmationFavorite: true });
    } else if (delta < -60) {
      this.onNextAffirmation();
    }
  },

  onNextAffirmation() {
    if (this.affItems.length === 0) {
      return;
    }
    this.affIndex = (this.affIndex + 1) % this.affItems.length;
    this.setData({ affirmationText: this.affItems[this.affIndex], affirmationFavorite: false });
  },

  onAffirmationFavorite() {
    this.setData({ affirmationFavorite: !this.data.affirmationFavorite });
  },

  onLibrary() {
    wx.navigateTo({ url: "/pages/story/list/index" });
  },

  onHistory() {
    wx.showToast({ title: "最近播放即将上线", icon: "none" });
  },
})
