/**
 * 故事详情 — 极简阅读版（§12.5 文本先行；音频未完成不影响阅读）。
 * UI Phase 3 按 §41.6.6 升级为沉浸封面 + 自定义播放器 + 环境音 + 收藏。
 */
import { storyApi } from "../../../services/api";
import { StoryResponse } from "../../../types/api";

Page({
  data: {
    storyId: 0,
    story: null as StoryResponse | null,
    paragraphs: [] as string[],
    loading: true,
  },

  onLoad(options: Record<string, string | undefined>) {
    this.setData({ storyId: Number(options.storyId || 0) });
    this.fetch();
  },

  fetch() {
    storyApi.detail(this.data.storyId).then((story) => {
      const paragraphs = (story.content || "")
        .split(/\n+/)
        .map((p) => p.trim())
        .filter((p) => p);
      this.setData({ story, paragraphs, loading: false });
    }).catch(() => {
      this.setData({ loading: false, story: null });
    });
  },

  goBack() {
    wx.navigateBack();
  },
})
