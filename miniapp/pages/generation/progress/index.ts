/**
 * 生成进度页（§5.2 步骤 E / §31.2 / §41.6.5）：
 * 前 20s 每 2s 轮询，之后每 4s；页面隐藏停止，恢复立即查询；
 * TEXT_READY → 故事详情（音频继续后台生成）；FAILED 可重试；BLOCKED 友好提示。
 * 不显示虚假百分比，只展示真实阶段文案。
 */
import { generationApi } from "../../../services/api";
import { getStatusBarHeight } from "../../../utils/safeArea";

const STAGE_TEXTS: Record<string, string> = {
  PENDING: "正在理解愿望",
  TEXT_GENERATING: "正在写你的故事",
  TEXT_MODERATING: "正在检查内容",
  TEXT_READY: "正在准备播放",
  AUDIO_SUBMITTING: "正在生成声音",
  AUDIO_PROCESSING: "正在生成声音",
  AUDIO_STORING: "正在准备播放",
};

let timer = 0;
let pollCount = 0;
let stopped = false;

Page({
  data: {
    statusBarHeight: getStatusBarHeight(),
    taskId: 0,
    state: "running", // running | failed | blocked
    stageText: "正在理解愿望",
    errorMessage: "",
    retrying: false,
  },

  onLoad(options: Record<string, string | undefined>) {
    this.setData({ taskId: Number(options.taskId || 0) });
    stopped = false;
    pollCount = 0;
    this.scheduleNext(0);
  },

  onShow() {
    if (stopped && this.data.state === "running") {
      stopped = false;
      this.scheduleNext(0); // 恢复立即查询（§31.2）
    }
  },

  onHide() {
    stopped = true;
    this.clearTimer();
  },

  onUnload() {
    stopped = true;
    this.clearTimer();
  },

  clearTimer() {
    if (timer) {
      clearTimeout(timer);
      timer = 0;
    }
  },

  scheduleNext(delayMs: number) {
    this.clearTimer();
    timer = setTimeout(() => this.poll(), delayMs) as unknown as number;
  },

  poll() {
    if (stopped || this.data.state !== "running") {
      return;
    }
    generationApi.task(this.data.taskId).then((task) => {
      if (stopped) {
        return;
      }
      if (task.status === "COMPLETED" || task.status === "TEXT_READY") {
        // 文本先可读：立即进入故事详情；音频状态在详情页继续看（§11.2）
        if (task.storyId) {
          wx.redirectTo({ url: "/pages/story/detail/index?storyId=" + task.storyId });
        }
        return;
      }
      if (task.status === "BLOCKED") {
        this.setData({ state: "blocked" });
        return;
      }
      if (task.status === "FAILED") {
        this.setData({ state: "failed", errorMessage: "生成遇到了点问题，可以再试一次。" });
        return;
      }
      this.setData({ stageText: STAGE_TEXTS[task.status] || "正在写你的故事" });
      // 前 20s 每 2s（10 次），之后每 4s（§31.2）
      pollCount += 1;
      this.scheduleNext(pollCount < 10 ? 2000 : 4000);
    }).catch(() => {
      // 网络抖动：按节拍继续轮询
      pollCount += 1;
      this.scheduleNext(pollCount < 10 ? 2000 : 4000);
    });
  },

  /** 再试一次：后端失败任务可能已被后台/管理端推进，重新轮询；无独立任务时提示回首页 */
  onRetry() {
    this.setData({ retrying: true });
    generationApi.task(this.data.taskId).then((task) => {
      if (task.status === "FAILED" || task.status === "BLOCKED") {
        this.setData({ retrying: false, state: task.status === "FAILED" ? "failed" : "blocked" });
        wx.showToast({ title: "任务仍在失败状态，请稍后再试", icon: "none" });
        return;
      }
      this.setData({ retrying: false, state: "running", errorMessage: "" });
      stopped = false;
      pollCount = 0;
      this.poll();
    }).catch(() => {
      this.setData({ retrying: false });
    });
  },

  goHome() {
    wx.switchTab({ url: "/pages/home/index" });
  },
})