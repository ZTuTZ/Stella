# PRODUCT_V1 — 产品与页面清单（摘要）

> 详细规范以根目录实施文档 §1~§5、§41~§42 为准，本文件为开发期速查。

## 一句话定义

根据用户真实背景、重要人物和目标，生成"目标已经实现后的未来生活故事"，再用 AI 生成沉浸式旁白音频，让用户每天听一段"未来的自己"。

## V1 核心闭环

微信登录 → Onboarding（昵称/状态/重要人物/目标）→ AI 追问 0~3 题 → Life Profile + Goal → 异步生成故事文本（完成后立即可读）→ 异步生成旁白音频 → 播放/收藏/重听 → 每日 Affirmations → 新目标或回访历史。

## 小程序页面（15 个）

```text
pages/
  launch/                 启动页 / 登录态判断
  onboarding/basic/       昵称、关注领域
  onboarding/context/     当前生活、重要人物
  onboarding/goal/        首个目标
  onboarding/followup/    AI 动态追问
  home/index/             首页
  goal/create/            创建新目标
  goal/detail/            目标详情
  generation/progress/    生成中
  story/list/             历史故事
  story/detail/           故事 + 音频播放器
  profile/index/          我的
  profile/edit/           编辑人生档案
  settings/index/         设置
pages/dev/design-system/  仅开发环境：设计系统展示页（UI Phase 0）
```

Tab Bar 仅 2 个：首页、我的。

## 管理后台页面

`/login`、`/dashboard`、`/users`、`/users/:id`、`/goals`、`/stories`、`/tasks`、`/prompts`、`/ai-logs`、`/settings`。

## V1 明确不做

社区/排行/会员支付/复杂积分/声音克隆/多语言/App/结果保证承诺；不复制 Stella 品牌资产。

## 关键业务规则（§32 摘）

1. 一个用户一份 Life Profile；修改 `profile_version +1`；故事记录生成时的版本。
2. 一个 Goal 可生成多个 Story；一个 Story 仅一份当前有效音频（旧音频 superseded）。
3. 文本、音频两阶段独立；音频失败不影响文本阅读。
4. 今日 Affirmation 按 user+date 唯一；所有删除默认软删除；输入输出均过 moderation。
