# 组件库（miniapp/components/）

设计系统组件（实施文档 §41.5/§41.10、UI Phase 0 §42.1）。全部组件四件套 `.wxml/.wxss/.ts/.json`。

| 组件 | 用途 | 关键属性 | 事件 |
|---|---|---|---|
| `dream-orb` | 统一视觉母题（120~220rpx） | `size` `breathing` | — |
| `primary-button` | 主按钮（深炭黑胶囊） | `text` `disabled` `loading` | `tap` |
| `pill-chip` | 胶囊选项/trending | `label` `selected` `small` | `tap` |
| `manifest-input` | 愿望胶囊输入 + ✦ 发送钮 | `value` `placeholder` | `change` `submit` |
| `story-card` | 故事卡（渐变 + Orb） | `title` `tone` `showPlay` `showFavorite` `favorite` `playing` | `tap` `play` `favorite` |
| `affirmation-card` | 叠卡肯定语 | `text` `footerLabel` `showActions` `favorite` | `tap` `favorite` `more` |
| `audio-player` | 播放器骨架（ready/generating/failed） | `title` `status` `playing` `progressPct` `currentTime` `duration` `ambience` `selectedAmbience` | `play` `seek` `ambience` `retry` |
| `brand-header` | 顶部品牌区（衬线字标 + 圆形图标） | — | `heart` `history` |
| `story-carousel` | 故事卡横向轮播（露边） | `stories` [{id,title,tone,favorite,playing}] | `storytap` |
| `mini-player` | 底部迷你播放器占位 | `visible` `title` `playing` `progressPct` `currentTime` `duration` | `play` |

## 使用约定

- 颜色/圆角/阴影一律取自 `styles/tokens.wxss`，页面禁止散落硬编码色值。
- 排版用 `styles/typography.wxss` 工具类（`.t-hero/.t-page-title/.t-card-title/.t-body/...`）。
- 动效用 `styles/animations.wxss`（`.anim-orb-breath/.anim-fade-up/...`），尊重系统减弱动效。
- 后续组件（brand-header、story-carousel、mini-player、soft-sheet、empty-state）随 UI Phase 1~4 增加。

## 组件内使用其他组件

`index.json` 的 `usingComponents` 用相对路径（如 audio-player 引 dream-orb）；页面层用绝对路径 `/components/...`。
