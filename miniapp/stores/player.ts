/**
 * 全局音频播放器（§31.3/§41.5.8）：
 * 单例 InnerAudioContext 贯穿故事详情页与 Mini Player，状态订阅式同步。
 * V1 不要求切后台播放；环境音 chips 仅为视觉选择（Mock 无混音能力）。
 */

export interface PlayerState {
  loaded: boolean;
  storyId: number | null;
  title: string;
  playing: boolean;
  currentTimeSec: number;
  durationSec: number;
  error: boolean;
}

let ctx: WechatMiniprogram.InnerAudioContext | null = null;

let state: PlayerState = {
  loaded: false,
  storyId: null,
  title: "",
  playing: false,
  currentTimeSec: 0,
  durationSec: 0,
  error: false,
};

const listeners = new Set<() => void>();

function notify(): void {
  listeners.forEach((fn) => fn());
}

export function getPlayerState(): PlayerState {
  return state;
}

/** 订阅状态变化；返回退订函数。 */
export function subscribePlayer(fn: () => void): () => void {
  listeners.add(fn);
  return () => {
    listeners.delete(fn);
  };
}

function ensureCtx(): void {
  if (ctx) {
    return;
  }
  ctx = wx.createInnerAudioContext();
  ctx.onPlay(() => {
    state = { ...state, playing: true, error: false };
    notify();
  });
  ctx.onPause(() => {
    state = { ...state, playing: false };
    notify();
  });
  ctx.onStop(() => {
    state = { ...state, playing: false };
    notify();
  });
  ctx.onEnded(() => {
    state = { ...state, playing: false, currentTimeSec: state.durationSec };
    notify();
  });
  ctx.onTimeUpdate(() => {
    if (!ctx) {
      return;
    }
    state = { ...state, currentTimeSec: ctx.currentTime, durationSec: ctx.duration || state.durationSec };
    notify();
  });
  ctx.onError(() => {
    state = { ...state, playing: false, error: true };
    notify();
  });
}

/** 载入故事音频（同故事幂等）；不自动播放。url 需为完整可访问地址。 */
export function loadAudio(storyId: number, title: string, url: string): void {
  ensureCtx();
  if (!ctx) {
    return;
  }
  const audioCtx = ctx;
  if (state.storyId === storyId && state.loaded) {
    return;
  }
  audioCtx.stop();
  state = {
    loaded: true,
    storyId,
    title,
    playing: false,
    currentTimeSec: 0,
    durationSec: 0,
    error: false,
  };
  audioCtx.src = url;
  notify();
}

export function togglePlay(): void {
  if (!ctx || !state.loaded) {
    return;
  }
  if (state.playing) {
    ctx.pause();
  } else {
    ctx.play();
  }
}

/** ±秒跳转（播放器 ±15 按钮）。 */
export function seekBy(deltaSec: number): void {
  if (!ctx || !state.loaded || state.durationSec <= 0) {
    return;
  }
  const target = Math.min(Math.max(state.currentTimeSec + deltaSec, 0), state.durationSec);
  ctx.seek(target);
  state = { ...state, currentTimeSec: target };
  notify();
}

/** mm:ss 展示格式。 */
export function formatTime(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds || 0));
  const m = Math.floor(s / 60);
  const r = s % 60;
  return `${String(m).padStart(2, "0")}:${String(r).padStart(2, "0")}`;
}

export function playerProgressPct(): number {
  if (state.durationSec <= 0) {
    return 0;
  }
  return Math.round((state.currentTimeSec / state.durationSec) * 100);
}
