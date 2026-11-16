/**
 * Library（§41.6.7 / UI Phase 4）：两列故事网格 + 全部/收藏筛选 + 上拉分页 + 空状态。
 * 收藏在卡片上直接切换（乐观更新 + PUT favorite）。
 */
import { storyApi } from "../../../services/api";
import { StoryResponse } from "../../../types/api";

const PAGE_SIZE = 10;

interface LibraryItem {
  id: number;
  title: string;
  tone: string;
  favorite: boolean;
}

Page({
  data: {
    favoriteOnly: false,
    items: [] as LibraryItem[],
    loading: true,
    loadingMore: false,
    page: 0,
    totalPages: 0,
    hasMore: false,
  },

  onLoad(options: Record<string, string | undefined>) {
    if (options.favorite === "1") {
      this.setData({ favoriteOnly: true });
    }
    this.reload();
  },

  onPullDownRefresh() {
    this.reload().then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (!this.data.hasMore || this.data.loadingMore) {
      return;
    }
    this.loadPage(this.data.page + 1, false);
  },

  toneOf(index: number): string {
    return index % 2 === 0 ? "pink" : "lilac";
  },

  reload(): Promise<void> {
    return this.loadPage(0, true);
  },

  loadPage(page: number, reset: boolean): Promise<void> {
    if (reset) {
      this.setData({ loading: this.data.items.length === 0, loadingMore: false });
    } else {
      this.setData({ loadingMore: true });
    }
    return storyApi.list(page, PAGE_SIZE, this.data.favoriteOnly).then((res) => {
      const items: LibraryItem[] = res.items.map((s: StoryResponse, i: number) => ({
        id: s.id,
        title: s.title,
        tone: this.toneOf(i),
        favorite: s.favorite,
      }));
      const merged = reset ? items : this.data.items.concat(items);
      this.setData({
        items: merged,
        page: res.page,
        totalPages: res.totalPages,
        hasMore: res.page + 1 < res.totalPages,
        loading: false,
        loadingMore: false,
      });
    }).catch(() => {
      this.setData({ loading: false, loadingMore: false });
    });
  },

  onFilterAll() {
    if (!this.data.favoriteOnly) {
      return;
    }
    this.setData({ favoriteOnly: false });
    this.reload();
  },

  onFilterFavorite() {
    if (this.data.favoriteOnly) {
      return;
    }
    this.setData({ favoriteOnly: true });
    this.reload();
  },

  onStoryTap(e: WechatMiniprogram.TouchEvent) {
    wx.navigateTo({ url: "/pages/story/detail/index?storyId=" + e.currentTarget.dataset.id });
  },

  onFavoriteTap(e: WechatMiniprogram.TouchEvent) {
    const id = Number(e.currentTarget.dataset.id);
    const item = this.data.items.find((it) => it.id === id);
    if (!item) {
      return;
    }
    const next = !item.favorite;
    // 乐观更新；收藏筛选下取消收藏则从列表移除
    const items = this.data.items.map((it) => (it.id === id ? { ...it, favorite: next } : it));
    this.setData({ items });
    storyApi.setFavorite(id, next).then(() => {
      if (this.data.favoriteOnly && !next) {
        this.reload();
      }
    }).catch(() => {
      const rollback = this.data.items.map((it) => (it.id === id ? { ...it, favorite: !next } : it));
      this.setData({ items: rollback });
    });
  },

  onCreate() {
    wx.switchTab({ url: "/pages/home/index" });
  },

  goBack() {
    wx.navigateBack();
  },
})
