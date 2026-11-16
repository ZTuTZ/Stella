# Future Self 微信小程序 V1 — 本地 Agent 可执行开发实施文档

**文档版本：** v1.1  
**文档日期：** 2026-11-16  
**项目代号：** `future-self-mini`  
**目标平台：** 微信小程序 + Web 管理后台  
**后端：** Java 21（允许 Java 17，但推荐 21）+ Spring Boot 4.1.x  
**数据库：** MySQL 8  
**缓存/辅助：** Redis（仅在需要处使用）  
**音频：** AI 音频 API（供应商文档后续放入项目目录，当前先按可插拔适配层实现）
**视觉方向：** Stella-inspired，小程序视觉与交互尽量接近 Stella 的公开设计语言，但不复制其品牌资产

> 本文档的用途不是做产品介绍，而是作为本地编码 Agent 的“开发合同”。Agent 应按本文档的模块边界、数据结构、接口、状态机、目录结构和验收标准逐步实现，不得自行扩展第一版范围，不得在缺少第三方 API 文档时臆造接口。

---

## 1. 项目目标与 V1 成功标准

### 1.1 产品一句话定义

一个根据用户真实背景、重要人物和目标，生成“目标已经实现后的未来生活故事”，再使用 AI 生成沉浸式旁白音频，让用户每天听一段“未来的自己”的微信小程序。

### 1.2 V1 需要验证的核心假设

V1 不以商业化和功能数量为目标，只验证以下三件事：

1. 用户愿意完成一次 2～5 分钟的人生档案/目标输入。
2. AI 生成的未来故事足够个性化，让用户产生“这说的是我”的感受。
3. 用户愿意第二天或之后再次回来听新的内容或重听历史故事。

### 1.3 V1 核心闭环

```text
微信进入小程序
  ↓
微信登录 / 建立用户
  ↓
Onboarding：昵称 + 当前状态 + 重要人物 + 想实现的目标
  ↓
AI 根据目标动态追问 0～3 个问题
  ↓
形成 Life Profile + Goal
  ↓
异步生成未来故事文本
  ↓
文本完成后立即可阅读
  ↓
异步调用 AI 音频 API 生成旁白音频
  ↓
音频完成后可播放 / 收藏 / 重听
  ↓
每天生成个性化 Affirmations
  ↓
用户创建新目标或返回历史故事
```

### 1.4 V1 明确不做

- 不做社区、动态、评论、点赞、私信、关注关系。
- 不做公开排行榜。
- 不做复杂会员体系、连续包月、微信支付。
- 不做复杂积分体系。
- 不做 AI 声音克隆，除非后续音频供应商文档明确支持且单独立项。
- 不做多语言。
- 不做 Android/iOS App。
- 不做“玄学结果保证”或声称目标必然实现；产品定位为想象/自我激励/沉浸式未来故事。
- 视觉与交互允许尽量复刻 Stella 的公开设计语言（粉色/奶油色背景、editorial 衬线标题、圆角卡片、照片式故事卡、Affirmation 滑卡、沉浸式音频播放器等），但不得直接复制 Stella 的名称、Logo、原始照片、插画、文案、音频、图标文件、字体文件或其他品牌资产。

---

## 2. V1 功能范围与优先级

### 2.1 P0：必须完成

| 模块 | 功能 | 验收口径 |
|---|---|---|
| 登录 | 微信 `wx.login` 登录 | 首次登录自动创建用户；后续能识别同一用户 |
| Onboarding | 昵称、当前状态、关注领域、重要人物、目标描述 | 可保存草稿，可完成，完成后不重复强制进入 |
| Life Profile | 查看和编辑人生档案 | 修改后影响后续生成，不追溯修改已生成故事 |
| Goal | 创建目标/愿望 | 至少包含标题、描述、类别、状态 |
| AI 追问 | 基于用户输入生成 0～3 个追问 | 返回结构化 JSON；用户可跳过问题 |
| AI 故事 | 生成个性化未来故事 | 故事与用户资料有明确关联；结构化返回标题、正文、摘要 |
| 生成任务 | 异步任务 + 状态查询 | 任务可失败、重试、恢复；数据库为事实源 |
| AI 音频 | 将故事转为 AI 旁白音频 | 通过适配器调用；音频 API 文档未到位时使用 Mock Provider |
| 音频播放 | 播放/暂停/进度/重播 | 音频生成后可正常播放；文本在音频未完成时仍可阅读 |
| 历史故事 | 故事列表、详情、收藏、删除 | 仅能访问自己的故事 |
| 今日 Affirmation | 每天生成一组个性化肯定句 | 同一天默认复用，不重复浪费模型调用 |
| 首页 | 今日故事 + Affirmation + 新建目标入口 | 完成核心闭环 |
| 我的 | 资料、人生档案、历史、设置 | 基础管理入口 |
| 内容安全 | 输入/输出审核接口与风险状态 | 即使第一阶段使用简单规则，也必须留完整接口 |
| AI 标识 | AI 生成文本/音频明确标识 | UI 展示“AI 生成内容” |
| 管理后台 | 用户、目标、故事、任务、Prompt、错误日志 | 内测排查问题所需最小能力 |
| 日志监控 | 请求 ID、任务日志、AI 调用日志 | 能定位一次失败生成的完整链路 |

### 2.2 P1：V1 稳定后再做

- 多种 AI 旁白声音选择。
- 用户自定义音频风格、情绪、语速。
- 分享卡片/海报。
- 微信服务通知提醒。
- 连续使用天数（streak）。
- 一键“换一个版本”。
- 目标完成/归档仪式。
- 轻量数据统计看板。

### 2.3 P2：暂不进入开发计划

- 支付与会员。
- 社区。
- 用户上传真实声音并克隆。
- 复杂 Journal / Gratitude Journal。
- Wall of Manifestations。
- 多人关系图谱。
- 推荐算法。

---

## 3. 用户角色与权限

### 3.1 普通用户 `USER`

仅能访问自己的：

- 用户资料
- Life Profile
- Goals
- Stories
- Audio Assets
- Affirmations
- Generation Tasks

所有根据 ID 获取资源的接口都必须再次校验 `owner_user_id`，不得只依赖前端传参。

### 3.2 管理员 `ADMIN`

内测阶段只有少量管理员。权限包括：

- 查看用户与最近活跃时间。
- 查看用户 Life Profile（注意后台展示敏感字段时应明确用途）。
- 查看 Goals / Stories / Generation Tasks。
- 查看 AI 请求耗时、错误、供应商返回。
- 启用/停用 Prompt 模板版本。
- 手动重试失败任务。
- 封禁异常用户。

V1 不做复杂 RBAC；仅 `USER`、`ADMIN` 两类角色。

---

## 4. 信息架构与页面清单

### 4.1 小程序页面

建议使用微信原生小程序 + TypeScript，不引入跨端框架，减少首版依赖和构建复杂度。

```text
miniapp/
  pages/
    launch/                 # 启动页 / 登录态判断
    onboarding/basic/       # 昵称、关注领域
    onboarding/context/     # 当前生活、重要人物
    onboarding/goal/        # 首个目标
    onboarding/followup/    # AI 动态追问
    home/index/             # 首页
    goal/create/            # 创建新目标
    goal/detail/            # 目标详情
    generation/progress/    # 生成中
    story/list/             # 历史故事
    story/detail/           # 故事 + 音频播放器
    profile/index/          # 我的
    profile/edit/           # 编辑人生档案
    settings/index/         # 设置
```

### 4.2 推荐 Tab Bar

V1 仅保留 2 个 Tab：

- 首页
- 我的

“创建目标”“故事历史”等作为页面入口，不占用底部 Tab。

### 4.3 管理后台页面

```text
admin-web/
  /login
  /dashboard
  /users
  /users/:id
  /goals
  /stories
  /tasks
  /prompts
  /ai-logs
  /settings
```

---

## 5. 小程序关键交互规范

### 5.1 首次进入

1. 调用 `wx.login()` 获取 code。
2. 调用后端 `/api/v1/auth/wechat/login`。
3. 后端返回 access token + 用户状态。
4. 若 `onboardingCompleted=false`，进入 onboarding。
5. 若已完成，进入首页。

V1 不强制获取微信头像和昵称；昵称由用户主动填写，避免把微信资料授权作为核心流程阻断点。

### 5.2 Onboarding 步骤

#### 步骤 A：基本信息

字段：

- `displayName`：必填，1～30 字符。
- `focusAreas`：多选，1～5 个。
  - 事业
  - 财富
  - 爱情
  - 家庭
  - 自信
  - 健康生活方式
  - 学习成长
  - 旅行
  - 其他

#### 步骤 B：当前生活与重要人物

字段：

- `currentSituation`：可选，0～1000 字。
- `importantPeople`：0～8 人；每项包含名称/称呼、关系、补充说明。
- `preferences`：可选，用于记录理想生活细节。

#### 步骤 C：首个目标

字段：

- `goalCategory`：必填。
- `goalTitle`：必填，2～80 字。
- `goalDescription`：必填，10～1200 字。
- `targetTimeText`：可选，如“一年后”“2027 年底前”；V1 不做复杂日期语义解析。

#### 步骤 D：AI 追问

- 后端调用 LLM 生成 0～3 个问题。
- 每个问题必须短、具体、对生成故事有帮助。
- 用户可以逐题回答，也可以点击“跳过”。
- 所有回答保存到 Goal 的 `details_json`。

#### 步骤 E：开始生成

- 创建 Generation Task。
- 跳转生成进度页。
- 文本生成成功后即可进入故事详情阅读。
- 音频继续后台生成；详情页轮询音频状态。

### 5.3 首页

从上到下：

1. 问候 + 昵称。
2. 今日主卡：优先展示今日生成或最近一条故事。
3. 主 CTA：“创建一个新的未来”。
4. 今日 Affirmation 卡片组。
5. 最近故事 3 条。

首页不得堆模型选择、Prompt 参数、技术名词。

### 5.4 故事详情页

必须包含：

- 故事标题。
- “AI 生成内容”标识。
- 音频播放器区域。
- 音频状态：生成中 / 可播放 / 失败可重试。
- 故事正文。
- 收藏按钮。
- 删除入口。
- 创建相似新故事入口（P1 可再开放）。

音频未完成时，禁止让用户一直停留在等待页；文本一旦完成应立即可读。

---

## 6. 技术选型

### 6.1 后端

推荐固定为：

- Java 21 LTS。
- Spring Boot 4.1.x。
- Maven。
- Spring Web MVC。
- Spring Validation。
- Spring Security（JWT/token filter + ADMIN 登录）。
- Spring Data JPA（V1 使用；复杂查询可使用原生 SQL）。
- MySQL Connector/J。
- Flyway。
- Spring Data Redis。
- Jackson。
- Micrometer / Actuator。
- Lombok 可用，但不要在核心 Domain Entity 上过度使用 `@Data`。

原则：不用若依，不引入大型脚手架，不做微服务。

### 6.2 小程序

- 微信原生小程序。
- TypeScript。
- WXML + WXSS。
- 封装统一 request client。
- API 类型统一放在 `types/api.ts` 或按模块拆分。

### 6.3 管理后台

- Vue 3 + TypeScript。
- Vite。
- Vue Router。
- Pinia。
- Axios。
- Element Plus。

后台仅为内测运维，不做重度视觉定制。

### 6.4 数据库与 Redis

MySQL 是所有业务状态的事实源。Redis 只用于：

- 登录 token/session 缓存（可选）。
- 接口限流。
- 短期幂等键。
- 分布式锁（只有多实例时才启用）。
- Prompt / 配置缓存。
- 今日 Affirmation 快速缓存（数据库仍保留记录）。

不得只在 Redis 保存任务状态、故事状态、付费状态或用户关键资料。

---

## 7. 总体架构

```text
┌──────────────────────┐
│   微信小程序          │
└──────────┬───────────┘
           │ HTTPS JSON
           ▼
┌─────────────────────────────────────┐
│ Spring Boot 单体应用                 │
│                                     │
│ Auth  Profile  Goal  Story          │
│ Affirmation  Generation  Admin      │
│ AI Text Adapter  AI Audio Adapter   │
│ Storage Adapter  Moderation Adapter │
└───────┬──────────┬──────────┬───────┘
        │          │          │
        ▼          ▼          ▼
     MySQL 8     Redis      文件存储
        │                     │
        │                     └─ dev: 本地文件
        │                        prod: OSS/COS/S3兼容
        │
        ├───────────────┐
        ▼               ▼
   文本 AI API       AI 音频 API
                     （文档后续提供）

┌──────────────────────┐
│ Vue 3 管理后台        │
└──────────┬───────────┘
           └────── HTTPS ──────> Spring Boot
```

### 7.1 架构原则

1. 单体优先，不拆微服务。
2. 外部 AI 服务全部通过 Adapter 隔离。
3. 生成任务异步执行。
4. 数据库保存完整任务状态，可重启恢复。
5. 音频供应商 URL 若有过期可能，必须下载/转存到自己的存储。
6. Prompt 必须版本化，不能散落硬编码。
7. 所有生成结果应保存所使用的 Prompt Version、Model、Provider，便于回溯。

---

## 8. 推荐项目目录

```text
future-self-mini/
├─ README.md
├─ AGENTS.md
├─ .env.example
├─ docs/
│  ├─ PRODUCT_V1.md
│  ├─ API_V1.md
│  ├─ DATABASE_V1.md
│  ├─ PROMPTS.md
│  ├─ RUNBOOK.md
│  └─ external/
│     └─ audio/
│        ├─ README.md
│        └─ <用户后续放入的音频API文档>
├─ backend/
│  ├─ pom.xml
│  └─ src/
│     ├─ main/java/com/futureself/
│     │  ├─ FutureSelfApplication.java
│     │  ├─ common/
│     │  ├─ config/
│     │  ├─ auth/
│     │  ├─ user/
│     │  ├─ profile/
│     │  ├─ goal/
│     │  ├─ story/
│     │  ├─ affirmation/
│     │  ├─ generation/
│     │  ├─ ai/text/
│     │  ├─ ai/audio/
│     │  ├─ moderation/
│     │  ├─ storage/
│     │  └─ admin/
│     └─ main/resources/
│        ├─ application.yml
│        ├─ application-local.yml
│        └─ db/migration/
├─ miniapp/
│  ├─ app.ts
│  ├─ app.json
│  ├─ app.wxss
│  ├─ pages/
│  ├─ components/
│  ├─ services/
│  ├─ stores/
│  ├─ types/
│  └─ utils/
├─ admin-web/
│  ├─ package.json
│  └─ src/
└─ scripts/
   ├─ dev-check.sh
   └─ smoke-test.sh
```

### 8.1 `AGENTS.md` 必须包含

本项目根目录必须创建 `AGENTS.md`，写给所有后续本地 Agent：

- 先读本实施文档和 `/docs`。
- 不允许自行增加 P1/P2 功能。
- 修改数据库必须新建 Flyway migration，禁止直接改历史 migration。
- 音频 API 文档不存在时，禁止臆造 endpoint/header/签名方式。
- 外部 API 必须封装在 adapter/client 层。
- 每个阶段完成后必须运行编译、单测和 smoke test。
- 不允许把 API key、微信 secret 写入 Git。
- 每次提交前更新相关 README/API 文档。

---

## 9. 后端模块划分

### 9.1 `auth`

职责：

- 微信 code 登录。
- access token 创建与验证。
- 管理后台登录。
- 当前用户上下文。

关键类建议：

```text
WechatAuthController
WechatAuthService
WechatApiClient
TokenService
CurrentUser
AuthFilter
AdminAuthController
```

### 9.2 `profile`

职责：

- Life Profile CRUD。
- Onboarding 完成状态。
- 重要人物、偏好、当前状态等资料。

### 9.3 `goal`

职责：

- Goal CRUD。
- AI 追问。
- 保存追问回答。
- 发起故事生成。

### 9.4 `story`

职责：

- 故事查询。
- 收藏。
- 软删除。
- 今日/最近故事选择。

### 9.5 `generation`

职责：

- 创建任务。
- 执行文本生成。
- 执行音频生成。
- 重试。
- 状态机。
- 任务恢复。

### 9.6 `ai/text`

职责：

- LLM 统一接口。
- OpenAI-compatible 实现（可作为默认通用实现）。
- Mock 实现。
- Prompt 装配。
- JSON Schema/结构化结果解析。

### 9.7 `ai/audio`

职责：

- AI 音频统一接口。
- Mock Provider。
- 用户后续提供 API 文档后实现真实 Provider。
- Provider Task 轮询/回调处理。
- 音频下载和转存。

### 9.8 `affirmation`

职责：

- 每日个性化肯定句。
- 当日幂等。
- 历史查询（V1 后端保留，前端可只展示当天）。

### 9.9 `moderation`

职责：

- 输入审核。
- 输出审核。
- 风险分类。
- 外部审核服务的适配器接口。
- V1 可先有 `RuleBasedModerationProvider`，后续接真实服务。

### 9.10 `storage`

职责：

- 保存音频文件。
- 返回稳定 URL。
- Dev 本地文件实现。
- Prod 对象存储实现。

---

## 10. 核心数据模型

所有业务表统一字段建议：

```text
id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY
created_at DATETIME(3) NOT NULL
updated_at DATETIME(3) NOT NULL
```

### 10.1 `app_user`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| openid | VARCHAR(64) | 微信小程序 openid，唯一 |
| unionid | VARCHAR(64) NULL | 有则保存 |
| display_name | VARCHAR(64) | 用户显示名 |
| role | VARCHAR(20) | USER / ADMIN |
| status | VARCHAR(20) | ACTIVE / DISABLED |
| onboarding_completed | TINYINT(1) | 是否完成 onboarding |
| last_active_at | DATETIME(3) | 最近活跃 |
| created_at | DATETIME(3) | 创建时间 |
| updated_at | DATETIME(3) | 更新时间 |

索引：

- UNIQUE `uk_user_openid(openid)`
- INDEX `idx_user_last_active(last_active_at)`

### 10.2 `life_profile`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| user_id | BIGINT | 唯一，一对一 |
| current_situation | TEXT | 当前生活状态 |
| focus_areas_json | JSON | 关注领域 |
| important_people_json | JSON | 重要人物数组 |
| preferences_json | JSON | 理想生活偏好 |
| extra_json | JSON | 扩展资料 |
| profile_version | INT | 每次修改 +1 |
| created_at | DATETIME(3) | |
| updated_at | DATETIME(3) | |

### 10.3 `manifestation_goal`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| user_id | BIGINT | 所属用户 |
| category | VARCHAR(40) | 分类 |
| title | VARCHAR(120) | 标题 |
| description | TEXT | 原始描述 |
| target_time_text | VARCHAR(120) NULL | 用户自然语言时间 |
| details_json | JSON | AI 追问答案/补充信息 |
| status | VARCHAR(20) | ACTIVE / ARCHIVED / DELETED |
| created_at | DATETIME(3) | |
| updated_at | DATETIME(3) | |

索引：`idx_goal_user_status(user_id, status, created_at)`。

### 10.4 `story`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| user_id | BIGINT | 所属用户 |
| goal_id | BIGINT | 来源目标 |
| generation_task_id | BIGINT | 来源任务 |
| title | VARCHAR(200) | 故事标题 |
| summary | VARCHAR(500) | 首页摘要 |
| content | MEDIUMTEXT | 完整正文 |
| perspective | VARCHAR(20) | SECOND_PERSON 等 |
| prompt_template_id | BIGINT NULL | Prompt 版本 |
| provider | VARCHAR(60) | 文本 AI provider |
| model | VARCHAR(100) | 模型 |
| profile_version | INT | 生成时用户资料版本 |
| is_favorite | TINYINT(1) | 收藏 |
| status | VARCHAR(20) | ACTIVE / DELETED / BLOCKED |
| moderation_status | VARCHAR(20) | PENDING / PASSED / BLOCKED |
| created_at | DATETIME(3) | |
| updated_at | DATETIME(3) | |

### 10.5 `audio_asset`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| user_id | BIGINT | 所属用户 |
| story_id | BIGINT | 对应故事 |
| provider | VARCHAR(60) | 音频供应商 |
| provider_task_id | VARCHAR(160) NULL | 供应商任务 ID |
| mode | VARCHAR(40) | NARRATION / NARRATION_MIX |
| voice_id | VARCHAR(120) NULL | 音色 |
| style | VARCHAR(120) NULL | 风格 |
| status | VARCHAR(30) | PENDING / SUBMITTED / PROCESSING / READY / FAILED |
| provider_url | TEXT NULL | 供应商结果 URL，仅内部使用 |
| storage_key | VARCHAR(500) NULL | 自有存储 key |
| public_url | TEXT NULL | 前端访问 URL |
| duration_ms | BIGINT NULL | 时长 |
| format | VARCHAR(20) | mp3/m4a/wav |
| request_json | JSON NULL | 脱敏后的请求摘要 |
| response_json | JSON NULL | 脱敏后的供应商响应摘要 |
| error_code | VARCHAR(100) NULL | 错误码 |
| error_message | VARCHAR(1000) NULL | 错误摘要 |
| created_at | DATETIME(3) | |
| updated_at | DATETIME(3) | |

### 10.6 `generation_task`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 |
| goal_id | BIGINT | 目标 |
| story_id | BIGINT NULL | 文本完成后写入 |
| task_type | VARCHAR(30) | STORY_WITH_AUDIO |
| status | VARCHAR(40) | 见状态机 |
| progress | INT | 0～100，展示用途 |
| retry_count | INT | 当前重试次数 |
| max_retry_count | INT | 默认 3 |
| next_retry_at | DATETIME(3) NULL | 下次重试 |
| locked_at | DATETIME(3) NULL | worker 锁定时间 |
| worker_id | VARCHAR(100) NULL | worker 标识 |
| error_stage | VARCHAR(40) NULL | TEXT / MODERATION / AUDIO / STORAGE |
| error_code | VARCHAR(100) NULL | |
| error_message | VARCHAR(1000) NULL | |
| started_at | DATETIME(3) NULL | |
| finished_at | DATETIME(3) NULL | |
| created_at | DATETIME(3) | |
| updated_at | DATETIME(3) | |

索引：

- `idx_task_status_retry(status, next_retry_at, created_at)`
- `idx_task_user(user_id, created_at)`

### 10.7 `affirmation_daily`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 |
| local_date | DATE | 用户日期 |
| items_json | JSON | 5～10 条 |
| prompt_template_id | BIGINT NULL | Prompt |
| provider | VARCHAR(60) | |
| model | VARCHAR(100) | |
| created_at | DATETIME(3) | |
| updated_at | DATETIME(3) | |

唯一索引：`uk_affirmation_user_date(user_id, local_date)`。

### 10.8 `prompt_template`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| prompt_key | VARCHAR(100) | FOLLOWUP / STORY / AFFIRMATION |
| version | INT | 版本 |
| system_prompt | MEDIUMTEXT | 系统 Prompt |
| user_template | MEDIUMTEXT | 用户 Prompt 模板 |
| output_schema_json | JSON NULL | 结构化输出 schema |
| enabled | TINYINT(1) | 是否当前启用 |
| notes | VARCHAR(500) NULL | |
| created_at | DATETIME(3) | |
| updated_at | DATETIME(3) | |

唯一索引：`uk_prompt_key_version(prompt_key, version)`。

### 10.9 `ai_call_log`

记录每次文本/音频/审核调用的可诊断信息，不保存 API key。

关键字段：

- `request_id`
- `user_id`
- `generation_task_id`
- `provider_type`：TEXT / AUDIO / MODERATION
- `provider`
- `model`
- `operation`
- `latency_ms`
- `success`
- `input_tokens` / `output_tokens`（有则记录）
- `error_code`
- `error_message`
- `request_summary_json`
- `response_summary_json`

### 10.10 `admin_user`

V1 使用独立管理员账号，不与小程序用户混用登录凭证。

字段：username、password_hash、status、last_login_at。

---

## 11. 生成任务状态机

### 11.1 状态定义

```text
PENDING
  ↓
TEXT_GENERATING
  ↓
TEXT_MODERATING
  ↓
TEXT_READY
  ↓
AUDIO_SUBMITTING
  ↓
AUDIO_PROCESSING
  ↓
AUDIO_STORING
  ↓
COMPLETED
```

失败：

```text
任意可重试阶段
  ↓
RETRY_WAIT
  ↓
回到失败阶段

超过 max_retry_count
  ↓
FAILED
```

审核拦截：

```text
TEXT_MODERATING
  ↓
BLOCKED
```

### 11.2 状态行为

- `TEXT_READY` 后前端即可进入故事详情页。
- `AUDIO_PROCESSING` 时显示“正在生成专属音频”。
- `FAILED` 时故事文本若已完成则仍然保留；允许仅重试音频阶段。
- 用户删除故事时采用软删除，不立即物理删除音频；可由定时清理任务处理。

### 11.3 Worker 设计

V1 不引入 Kafka/RabbitMQ。

采用：

- MySQL `generation_task` 作为任务队列事实源。
- `@Scheduled(fixedDelay=...)` worker 扫描可执行任务。
- 单实例可直接数据库行锁。
- 多实例时增加 Redis 分布式锁或 MySQL `SKIP LOCKED`。
- 每次只推进一个阶段，保证失败后可恢复。

不得用一个长事务包住外部 AI 调用。

---

## 12. API 设计

统一前缀：`/api/v1`。

统一响应：

```json
{
  "success": true,
  "code": "OK",
  "message": "",
  "data": {},
  "requestId": "..."
}
```

错误时 `success=false`，HTTP 状态码与业务错误同时使用。

### 12.1 Auth

#### `POST /api/v1/auth/wechat/login`

请求：

```json
{
  "code": "wx.login返回的code"
}
```

响应：

```json
{
  "accessToken": "...",
  "expiresIn": 7200,
  "user": {
    "id": 1,
    "displayName": "小雨",
    "onboardingCompleted": false
  }
}
```

### 12.2 Profile

- `GET /api/v1/me`
- `PATCH /api/v1/me`
- `GET /api/v1/me/life-profile`
- `PUT /api/v1/me/life-profile`
- `POST /api/v1/me/onboarding/complete`

### 12.3 Goals

- `POST /api/v1/goals`
- `GET /api/v1/goals`
- `GET /api/v1/goals/{id}`
- `PATCH /api/v1/goals/{id}`
- `DELETE /api/v1/goals/{id}`

#### `POST /api/v1/goals/{id}/followup-questions`

响应示例：

```json
{
  "questions": [
    {
      "id": "q1",
      "question": "当这个目标实现后，你最希望自己每天早晨醒来在哪里？",
      "inputType": "TEXT",
      "optional": true
    }
  ]
}
```

#### `PUT /api/v1/goals/{id}/followup-answers`

```json
{
  "answers": [
    {"questionId": "q1", "answer": "上海一个有大窗户的家里"}
  ]
}
```

### 12.4 Generation

#### `POST /api/v1/goals/{id}/generate-story`

幂等头建议：`Idempotency-Key`。

响应：

```json
{
  "taskId": 123,
  "status": "PENDING"
}
```

#### `GET /api/v1/generation-tasks/{id}`

```json
{
  "id": 123,
  "status": "AUDIO_PROCESSING",
  "progress": 75,
  "storyId": 456,
  "audioStatus": "PROCESSING",
  "error": null
}
```

### 12.5 Stories

- `GET /api/v1/stories?page=0&size=20&favorite=false`
- `GET /api/v1/stories/{id}`
- `PUT /api/v1/stories/{id}/favorite`
- `DELETE /api/v1/stories/{id}`
- `POST /api/v1/stories/{id}/retry-audio`

故事详情响应至少包含：

```json
{
  "id": 456,
  "title": "工作室月入十万后的普通早晨",
  "summary": "你从自己的家里醒来……",
  "content": "...",
  "aiGenerated": true,
  "favorite": false,
  "audio": {
    "status": "READY",
    "url": "https://...",
    "durationMs": 238000
  }
}
```

### 12.6 Affirmations

- `GET /api/v1/affirmations/today`

若当天不存在则服务端生成一次；并发请求必须通过数据库唯一索引或锁保证只生成一份。

### 12.7 首页聚合

`GET /api/v1/home`

一次返回：

- 用户昵称。
- 今日/最近故事。
- 今日 Affirmations。
- 最近故事列表。
- 是否存在正在生成的任务。

减少小程序首页多次请求。

### 12.8 Admin

- `/api/v1/admin/auth/login`
- `/api/v1/admin/users`
- `/api/v1/admin/users/{id}`
- `/api/v1/admin/goals`
- `/api/v1/admin/stories`
- `/api/v1/admin/tasks`
- `/api/v1/admin/tasks/{id}/retry`
- `/api/v1/admin/prompts`
- `/api/v1/admin/prompts/{id}`
- `/api/v1/admin/ai-logs`
- `/api/v1/admin/dashboard/summary`

---

## 13. 文本 AI 适配层

### 13.1 接口

```java
public interface TextAiProvider {
    <T> T generateStructured(TextAiRequest request, Class<T> responseType);
    String providerName();
}
```

`TextAiRequest` 至少包含：

- operation
- systemPrompt
- userPrompt
- outputSchema
- temperature
- maxTokens
- requestId

### 13.2 默认实现

实现两个 Provider：

1. `MockTextAiProvider`：本地无 key 时返回固定但结构正确的数据。
2. `OpenAiCompatibleTextAiProvider`：从环境变量读取 `baseUrl/apiKey/model`，兼容多数聚合平台。

配置：

```yaml
app:
  ai:
    text:
      provider: mock
      base-url: ${AI_TEXT_BASE_URL:}
      api-key: ${AI_TEXT_API_KEY:}
      model: ${AI_TEXT_MODEL:}
      timeout-seconds: 60
```

不得让 Controller 直接调用第三方 HTTP API。

---

## 14. Prompt 设计

### 14.1 追问 Prompt：`FOLLOWUP`

目标：只问真正能增加故事真实感的问题。

约束：

- 最多 3 个。
- 若用户信息已经足够，允许返回 0 个。
- 不重复用户已提供的信息。
- 问题具体、容易回答。
- 不问身份证、地址、财务账户等高敏感信息。

结构化输出：

```json
{
  "questions": [
    {
      "id": "q1",
      "question": "...",
      "reason": "...",
      "optional": true
    }
  ]
}
```

### 14.2 故事 Prompt：`STORY`

写作目标：

- 第二人称“你”。
- 写成“目标已经实现后的普通一天”，不是预测，不做真实性保证。
- 900～1800 中文字为默认范围，可配置。
- 具体生活细节多于抽象鸡汤。
- 至少自然引用 2～5 个用户独有信息；信息不足时不要编造具体真实姓名、住址、公司名。
- 语气温柔、克制、沉浸，不使用夸张神谕式表达。
- 避免把医疗、投资、法律、死亡等高风险结果写成确定事实。

结构建议：

1. 感官场景开场。
2. 目标已实现的生活证据。
3. 重要人物或熟悉物件出现。
4. 一处很具体的小细节。
5. 用户内心的平静/满足。
6. 自然收束。

结构化输出：

```json
{
  "title": "...",
  "summary": "...",
  "content": "...",
  "audioDirection": {
    "mood": "warm_calm",
    "pace": "slow",
    "notes": "自然、亲密、避免广告腔"
  }
}
```

### 14.3 Affirmation Prompt：`AFFIRMATION`

输出 6 条，每条建议 12～40 个中文字符。

要求：

- 与当前 Goal / Life Profile 相关。
- 不承诺外部事件必然发生。
- 使用第一人称。
- 可被每天朗读。
- 避免空泛重复。

输出：

```json
{
  "items": [
    "我正在把自己的能力变成越来越稳定的选择和收入。"
  ]
}
```

### 14.4 Prompt 版本管理

- 初始 Prompt 写入 Flyway seed migration。
- 后台编辑时创建新版本，不覆盖历史版本。
- 同一 `prompt_key` 只能有一个 `enabled=true`。
- 故事表保存 `prompt_template_id`。

---

## 15. AI 音频适配层（重点）

用户后续会把音频 API 文档放到：

```text
docs/external/audio/
```

### 15.1 在 API 文档到位前，Agent 必须做什么

必须完成：

- 音频领域模型。
- `AiAudioProvider` 接口。
- Mock Provider。
- 音频任务状态机。
- Provider 配置模型。
- 轮询/回调的扩展点。
- 文件转存逻辑接口。
- 小程序播放器全链路。

不得做：

- 猜测真实音频 API URL。
- 猜测鉴权 header。
- 猜测签名算法。
- 假装真实 Provider 已接通。

### 15.2 接口建议

```java
public interface AiAudioProvider {
    AudioProviderCapabilities capabilities();
    AudioSubmitResult submit(AudioGenerationRequest request);
    AudioQueryResult query(String providerTaskId);
    default boolean supportsWebhook() { return false; }
    String providerName();
}
```

```java
public record AudioGenerationRequest(
    String requestId,
    Long storyId,
    String text,
    String voiceId,
    String mood,
    String pace,
    AudioMode mode,
    Map<String, Object> options
) {}
```

```java
public enum AudioMode {
    NARRATION,
    NARRATION_MIX
}
```

`NARRATION_MIX` 表示供应商若支持，可一次生成“旁白 + 环境/氛围”的完整音频；若不支持，退化到 `NARRATION`，不要为 V1 额外构建复杂音频混音服务。

### 15.3 Provider 能力声明

```java
public record AudioProviderCapabilities(
    boolean async,
    boolean supportsWebhook,
    boolean supportsNarrationMix,
    boolean supportsVoiceSelection,
    boolean supportsSpeed,
    boolean resultUrlMayExpire
) {}
```

生成服务不得通过 `if (providerName.equals(...))` 到处写供应商特例，应通过 capabilities 和 adapter 封装差异。

### 15.4 Mock Provider

`MockAiAudioProvider` 必须能完成 UI 和任务状态联调：

- submit 后返回模拟 `providerTaskId`。
- 经过可配置延时后 query 返回 READY。
- READY 时返回项目内的测试音频 fixture。
- fixture 放在 `backend/src/test/resources/audio/` 或 dev media 目录。

### 15.5 真正 API 文档到位后的 Agent 行为

当 `docs/external/audio/` 出现真实文档后：

1. 先阅读全部音频 API 文档。
2. 在 `docs/API_AUDIO_PROVIDER_MAPPING.md` 写出：鉴权、提交、查询/回调、状态映射、错误码、结果字段、速率限制。
3. 新建供应商实现类，例如 `XxxAiAudioProvider`。
4. 不改业务层接口；仅替换 provider 配置即可。
5. 对真实 HTTP 响应保留脱敏日志。
6. 若返回音频 URL 有时效，READY 后立即下载并转存到 `StorageService`。
7. 添加真实 Provider 的 contract/integration test；没有 key 时测试自动跳过。

### 15.6 音频生成文本

故事正文不一定原样送给 TTS。应先通过 `AudioScriptBuilder` 做最小处理：

- 去除 Markdown。
- 处理不适合朗读的符号。
- 数字/英文可保留，由供应商处理。
- 过长文本按供应商限制分段；若供应商支持长文本，优先整段提交。

禁止为追求“更像真人”而自动插入大量没有依据的语气词。

---

## 16. Storage 设计

### 16.1 接口

```java
public interface StorageService {
    StoredObject save(String key, InputStream input, String contentType);
    Optional<StoredObject> get(String key);
    void delete(String key);
}
```

### 16.2 Dev 实现

`LocalStorageService`：

- 保存到 `./data/media/`。
- 后端通过受控静态路由提供访问。
- key 示例：`audio/{userId}/{storyId}/{uuid}.mp3`。

### 16.3 Production 实现

后续接 OSS/COS/S3 compatible。业务层不得依赖具体 SDK。

---

## 17. Redis 使用方案

V1 Redis Key 建议：

```text
fs:auth:token:{tokenHash}
fs:rate:{userId}:{operation}:{yyyyMMddHHmm}
fs:idempotency:{userId}:{key}
fs:prompt:{promptKey}
fs:affirmation:{userId}:{yyyyMMdd}
fs:lock:task-worker:{taskId}
```

TTL：

- token：与 access token 一致。
- rate limit：窗口时间 + 少量冗余。
- idempotency：24h。
- prompt：10 min，可主动失效。
- affirmation cache：到次日 + 冗余。

若 Redis 不可用：

- 核心生成和读取仍应可运行。
- 限流/缓存可降级。
- 不得因 Redis 重启丢失故事、任务或音频状态。

---

## 18. 微信登录设计

### 18.1 后端流程

```text
miniapp wx.login()
  ↓ code
POST /auth/wechat/login
  ↓
WechatApiClient.code2session
  ↓ openid/session_key/unionid?
根据 openid 查 app_user
  ↓
不存在 → 新建
  ↓
签发 access token
```

### 18.2 Secret 管理

环境变量：

```text
WECHAT_APP_ID=
WECHAT_APP_SECRET=
```

禁止：

- 提交到 Git。
- 返回给前端。
- 打到日志。

### 18.3 Access Token

V1 可采用后端自签 JWT 或随机 opaque token。推荐随机 opaque token + Redis/session 表，便于主动失效；若希望减少 Redis 依赖，可用 JWT。

只选择一种实现，不要同时维护两套。

---

## 19. 内容安全与产品边界

### 19.1 基本原则

产品应把生成内容表达为：

- 想象练习
- 未来故事
- 自我激励内容

不要表达为：

- “AI 预测你的未来”
- “保证显化成功”
- “照做一定能发财/治病/挽回某人”

### 19.2 Moderation 接口

```java
public interface ModerationProvider {
    ModerationResult check(ModerationRequest request);
}
```

阶段：

1. Goal 输入后检查。
2. LLM 输出故事后检查。
3. Affirmation 输出后检查。

### 19.3 V1 规则审核

没有外部审核服务时，实现最小 `RuleBasedModerationProvider`：

- 明显违法违规关键词。
- 自伤/伤人高风险表达。
- 对他人进行操控、跟踪、报复等目标。
- 明确要求医疗治愈、投资稳赚等确定性结果时，调整生成边界或阻断。

规则只是兜底，必须保留未来替换为真实审核服务的接口。

### 19.4 AI 标识

所有故事详情和 AI 音频区域展示：`AI 生成内容`。

数据库保留 `ai_generated=true` 的语义字段或默认事实，以便未来扩展。

---

## 20. 管理后台 V1

### 20.1 Dashboard

显示：

- 注册用户数。
- 今日活跃用户数。
- 今日新增目标。
- 今日故事生成数。
- 音频成功/失败数。
- 失败任务数。
- 文本 AI 平均耗时。
- 音频 AI 平均耗时。

### 20.2 用户详情

展示：

- 基础信息。
- Onboarding 状态。
- Life Profile。
- Goals。
- Stories。
- 最近任务与错误。

### 20.3 Prompt 管理

支持：

- 查看版本。
- 新建版本。
- 启用某版本。
- 禁用版本。
- 测试 Prompt（P1；V1 可不做在线试跑）。

### 20.4 任务管理

支持：

- 按状态筛选。
- 查看任务阶段、错误。
- 手动重试。
- 对“文本完成、音频失败”的任务只重试音频。

---

## 21. 配置设计

`application.yml` 不写 secret，只定义结构。

```yaml
server:
  port: 8080

spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/future_self?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}

app:
  auth:
    token-ttl-seconds: 7200
  generation:
    worker-delay-ms: 1500
    max-retry-count: 3
  ai:
    text:
      provider: ${AI_TEXT_PROVIDER:mock}
      base-url: ${AI_TEXT_BASE_URL:}
      api-key: ${AI_TEXT_API_KEY:}
      model: ${AI_TEXT_MODEL:}
    audio:
      provider: ${AI_AUDIO_PROVIDER:mock}
      base-url: ${AI_AUDIO_BASE_URL:}
      api-key: ${AI_AUDIO_API_KEY:}
      voice-id: ${AI_AUDIO_VOICE_ID:}
  storage:
    provider: ${STORAGE_PROVIDER:local}
    local-dir: ${STORAGE_LOCAL_DIR:./data/media}
```

`.env.example` 只放变量名和非敏感默认值。

---

## 22. 错误码规范

至少定义：

```text
AUTH_REQUIRED
AUTH_INVALID_TOKEN
WECHAT_LOGIN_FAILED
USER_DISABLED
PROFILE_NOT_FOUND
GOAL_NOT_FOUND
GOAL_CONTENT_BLOCKED
STORY_NOT_FOUND
TASK_NOT_FOUND
TASK_ALREADY_RUNNING
TASK_RETRY_LIMIT_REACHED
TEXT_AI_UNAVAILABLE
TEXT_AI_INVALID_RESPONSE
AUDIO_PROVIDER_NOT_CONFIGURED
AUDIO_SUBMIT_FAILED
AUDIO_PROCESSING_FAILED
AUDIO_STORAGE_FAILED
MODERATION_BLOCKED
RATE_LIMITED
VALIDATION_FAILED
INTERNAL_ERROR
```

前端不直接展示底层异常栈或供应商原始错误。

---

## 23. 日志与可观测性

每个 API 请求生成 `requestId`，并贯穿：

- HTTP 日志。
- Generation Task。
- Text AI 调用。
- Audio AI 调用。
- Storage 操作。

日志禁止包含：

- API key。
- 微信 app secret。
- 完整 access token。
- 完整 session_key。

AI 用户内容日志默认只保留摘要/截断文本，详细正文以数据库业务记录为准。

Actuator 至少开放：

- health
- info
- metrics（本地/内网）

---

## 24. 限流与防重复生成

V1 建议配置化：

- 创建 Goal：30 次/小时/用户。
- AI 追问：20 次/小时/用户。
- 生成故事：10 次/日/用户（内测可提高）。
- 重试音频：5 次/故事/日。

创建生成任务接口必须支持幂等：

- 前端生成 UUID 作为 `Idempotency-Key`。
- Redis 保存 24h；同时数据库逻辑检查同一 Goal 是否已有短时间内运行中任务。

---

## 25. 数据隐私与删除

### 25.1 用户删除故事

V1：软删除故事，前端不可见。

### 25.2 后台物理清理

每日定时任务清理超过保留期限的已删除音频。

### 25.3 用户注销

V1 至少预留 service：

```java
UserDeletionService.requestDeletion(userId)
```

即使 UI 暂不开放，也不要让数据模型无法清理。

---

## 26. 测试策略

### 26.1 后端单元测试

必须覆盖：

- Goal 校验。
- Life Profile 更新版本。
- PromptBuilder。
- Followup JSON 解析。
- Story JSON 解析。
- Generation Task 状态机。
- Retry 策略。
- Audio Provider capabilities 分支。
- 用户资源权限校验。

### 26.2 后端集成测试

至少覆盖：

1. 新用户登录 → onboarding → 建 goal。
2. Mock 文本 AI → 生成 story。
3. Mock 音频 → audio READY。
4. 任务失败一次 → 重试成功。
5. 用户 A 不可访问用户 B 的 story。
6. 当日 Affirmation 并发请求只生成一份。

优先使用 Testcontainers MySQL 8；若开发机无 Docker，提供 `local-test` profile 连接独立测试数据库。

### 26.3 小程序测试

必须手工/自动 smoke：

- 首次登录。
- Onboarding 中途退出再进入可恢复。
- 生成中页面。
- 文本先完成、音频仍生成。
- 音频 READY 后播放。
- 弱网/请求失败可重试。
- 历史列表与收藏。

### 26.4 Admin 测试

- 登录。
- 用户列表。
- 查看失败任务。
- 手动重试。
- Prompt 新版本启用。

---

## 27. 本地开发启动要求

### 27.1 前置环境

- JDK 21（或 17）。
- Maven 3.6.3+；推荐本机当前稳定 Maven。
- MySQL 8。
- Redis 6+ / 7+。
- Node.js 当前 LTS。
- 微信开发者工具。

### 27.2 初始化数据库

只创建空库：

```sql
CREATE DATABASE future_self
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
```

表结构由 Flyway 自动创建。

### 27.3 本地启动顺序

```text
1. MySQL
2. Redis
3. backend
4. admin-web（需要时）
5. 微信开发者工具打开 miniapp
```

### 27.4 无 AI Key 时

项目必须仍然可以完整演示：

```text
AI_TEXT_PROVIDER=mock
AI_AUDIO_PROVIDER=mock
```

Mock 模式必须走真实数据库和任务状态机，只替换第三方服务响应，不能跳过业务流程。

---

## 28. 本地 Agent 分阶段执行计划

这是最重要的执行章节。Agent 必须按顺序完成，每个阶段可单独提交。

### Phase 0 — 建项目骨架

任务：

- 创建根目录结构。
- 创建 `AGENTS.md`、`.gitignore`、`.env.example`。
- 初始化 Spring Boot。
- 初始化 miniapp。
- 初始化 admin-web。
- 后端可启动，提供 `/actuator/health`。

验收：

- `mvn test` 成功。
- 后端启动无异常。
- 小程序空首页可打开。
- admin-web 可启动。

### Phase 1 — 数据库与基础设施

任务：

- 配置 MySQL、Redis、Flyway。
- 创建 V1 所有核心表 migration。
- 统一异常、响应体、requestId。
- 创建 storage interface + local storage。

验收：

- 新空库启动自动迁移成功。
- 二次启动 migration 不重复执行。
- 健康检查可看到 DB/Redis 状态。

### Phase 2 — Auth + User + Profile

任务：

- WechatApiClient。
- 开发环境增加 mock login 配置，避免每次依赖真实微信。
- 用户创建/登录。
- Life Profile CRUD。
- Onboarding 状态。

验收：

- mock 登录后可拿 token。
- 完成 onboarding 后再次登录直接首页。
- 用户无法访问其他用户资料。

### Phase 3 — Goal + AI Followup

任务：

- Goal CRUD。
- `TextAiProvider` + Mock + OpenAI-compatible Provider。
- PromptTemplate。
- Followup Prompt + 结构化解析。
- 保存回答。

验收：

- 使用 Mock 可以完成完整追问。
- 配置真实兼容 API 后无需改业务代码。
- JSON 返回异常时有明确错误与日志。

### Phase 4 — Story Generation

任务：

- `generation_task` worker。
- STORY Prompt。
- 故事结构化返回。
- 输入/输出 moderation。
- 创建 `story`。
- 小程序生成进度页和故事详情文本。

验收：

- 任务能从 PENDING 走到 TEXT_READY。
- 后端重启后未完成任务可继续。
- 文本 AI 失败可重试。
- Story 明确关联 Profile Version / Prompt Version / Model。

### Phase 5 — AI Audio Framework

任务：

- `AiAudioProvider`。
- `MockAiAudioProvider`。
- `audio_asset`。
- Audio 阶段状态机。
- Storage 转存。
- 小程序播放器。

验收：

- Mock 音频能从 AUDIO_SUBMITTING 走到 COMPLETED。
- 文本完成后音频失败不影响文本阅读。
- 音频可重新单独生成。
- 音频 API 文档缺失时没有任何伪造的真实 Provider 代码。

### Phase 6 — 接入真实 AI 音频 API（条件阶段）

**触发条件：** `docs/external/audio/` 内存在用户提供的供应商文档。

任务：

- 读取文档。
- 产出 `API_AUDIO_PROVIDER_MAPPING.md`。
- 实现真实 provider。
- 状态映射。
- 错误码映射。
- 结果转存。
- 集成测试。

验收：

- 配置 `AI_AUDIO_PROVIDER=<name>` 即可切换。
- Mock 和真实 Provider 的上层业务代码完全一致。
- 无 API key 时不会启动失败，可回落 Mock 或明确报配置错误。

### Phase 7 — Affirmation + Home + Story Library

任务：

- 每日 Affirmation。
- 首页聚合接口。
- 首页 UI。
- 故事历史、收藏、删除。
- 我的页面。

验收：

- 同一用户同一天只生成一组 Affirmation。
- 首页 1 个接口可拿主数据。
- 故事列表分页正确。

### Phase 8 — 管理后台 + 稳定性

任务：

- Admin 登录。
- 用户/目标/故事/任务列表。
- Prompt 管理。
- AI log。
- 手动重试。
- 限流。
- 日志脱敏。
- README/RUNBOOK。

验收：

- 可以在后台定位任意失败 task。
- 可以切 Prompt 版本。
- 可以重试失败音频。
- 关键流程 smoke test 全部通过。

### Phase 9 — V1 发布前清单

必须逐项确认：

- 所有 secret 均来自环境变量。
- 无测试账号密码写死。
- 无 provider API key 进入前端包。
- 所有用户资源接口做 owner 校验。
- 所有 AI 结果有标识。
- 音频 URL 使用 HTTPS。
- Story 文本可在音频失败时使用。
- 任务有 retry limit，不无限重试。
- 日志不打印用户 token 和 provider secret。
- 数据库 migration 在全新 MySQL 8 上执行成功。
- README 包含启动方式。
- `AGENTS.md` 与代码现状一致。

---

## 29. Definition of Done（每个开发任务）

一个任务只有同时满足以下条件才算完成：

1. 功能代码完成。
2. 必要 migration 完成。
3. 接口 DTO 有校验。
4. 权限校验完成。
5. 错误处理完成。
6. 至少包含关键单元测试。
7. `mvn test` 通过。
8. 前端无 TypeScript 编译错误。
9. 文档同步更新。
10. 没有新增 secret、硬编码供应商 key、伪造 API。

---

## 30. 推荐的后端包结构

```text
com.futureself
├─ common
│  ├─ api
│  ├─ error
│  ├─ logging
│  ├─ security
│  └─ util
├─ config
├─ auth
│  ├─ api
│  ├─ application
│  ├─ domain
│  └─ infrastructure
├─ user
├─ profile
├─ goal
├─ story
├─ affirmation
├─ generation
├─ ai
│  ├─ text
│  │  ├─ TextAiProvider.java
│  │  ├─ MockTextAiProvider.java
│  │  └─ OpenAiCompatibleTextAiProvider.java
│  └─ audio
│     ├─ AiAudioProvider.java
│     ├─ MockAiAudioProvider.java
│     └─ provider/
├─ moderation
├─ storage
└─ admin
```

不要求严格 DDD；目录分层的目的只是避免 Controller、Repository、第三方 API 混在一起。

---

## 31. 前端状态与 API 约定

### 31.1 小程序统一 request client

必须实现：

- base URL。
- Authorization header。
- requestId 透传。
- 401 自动清理登录态并重新登录。
- 业务错误统一 Toast/页面错误处理。
- 超时。

### 31.2 Generation Progress

轮询策略：

- 前 20 秒：每 2 秒一次。
- 20 秒后：每 4 秒一次。
- 页面隐藏后停止轮询。
- 页面恢复时立即查询一次。
- 达到 `TEXT_READY` 后可自动进入 Story 页面；Story 页面继续低频查询 audio 状态。

不得在前端假设音频一定会在固定秒数完成。

### 31.3 播放器

封装 `AudioPlayer` component：

- play/pause。
- seek。
- currentTime/duration。
- loading/error。
- ended。

V1 不要求小程序切后台继续播放；如果后续需要后台播放，单独评估微信能力和审核要求，不要阻塞首版。

---

## 32. 关键业务规则

1. 一个用户只有一个当前 Life Profile。
2. Life Profile 修改会增加 `profile_version`。
3. Story 生成时记录当时 `profile_version`，历史故事不跟随资料修改。
4. 一个 Goal 可以生成多个 Story。
5. 一个 Story V1 只维护一个当前有效 Audio Asset；重生成音频时旧音频可标记 superseded。
6. 文本和音频是两个阶段，音频失败不得把整个 Story 判为不可用。
7. 今日 Affirmation 按用户 + 日期唯一。
8. 所有删除默认软删除。
9. 用户输入和 AI 输出都经过 moderation。
10. 所有第三方调用都有超时、重试上限和 requestId。

---

## 33. Prompt 输入上下文组装规则

`StoryPromptContext` 建议字段：

```text
user.displayName
profile.currentSituation
profile.focusAreas
profile.importantPeople
profile.preferences
goal.category
goal.title
goal.description
goal.targetTimeText
goal.detailsJson
```

上下文组装时：

- 空字段不发送。
- 限制总长度。
- 不把后台管理员备注发送给模型。
- 不把 openid、userId 等技术标识发送给模型。
- 不把历史完整故事默认塞进上下文，除非后续专门设计“避免重复”能力。

---

## 34. V1 数据指标

后台至少记录/可计算：

- 新增用户数。
- Onboarding 完成率。
- 首个 Goal 创建率。
- 首个 Story 成功率。
- 音频生成成功率。
- Story 播放率。
- 次日回访率（可用 last_active + event 简单计算）。
- 人均生成故事数。
- 平均故事生成耗时。
- 平均音频生成耗时。

V1 不需要接第三方埋点平台；可增加简单 `user_event` 表，或使用结构化日志。若创建 `user_event` 表，事件只记录产品行为，不记录完整故事正文。

---

## 35. 可选 `user_event` 表

建议首版实现，便于验证产品是否成立：

字段：

- user_id
- event_name
- entity_type
- entity_id
- properties_json
- occurred_at

事件：

```text
APP_OPEN
ONBOARDING_STARTED
ONBOARDING_COMPLETED
GOAL_CREATED
FOLLOWUP_COMPLETED
STORY_REQUESTED
STORY_TEXT_READY
AUDIO_READY
STORY_OPENED
AUDIO_PLAY_STARTED
AUDIO_PLAY_COMPLETED
STORY_FAVORITED
```

---

## 36. 本地 Agent 禁止事项

Agent 在开发时禁止：

- 自己决定加入支付、会员、社区、签到等未在 P0 的功能。
- 把第三方 API 写在 Controller。
- 在音频 API 文档没出现时搜索互联网并猜测用户准备使用哪个供应商。
- 将 mock provider 写成“看起来像真实 provider”的伪实现。
- 用 Redis 代替数据库保存任务。
- 让 AI 调用运行在数据库长事务内。
- 无限制自动重试第三方服务。
- 把完整用户隐私资料写入普通 info 日志。
- 在数据库表中存明文 API key。
- 把 prompt 只写死在 Java 字符串中而没有版本记录。
- 为追求架构感拆微服务。

---

## 37. 当用户把音频 API 文档放入项目后，给 Agent 的直接指令

可以把下面这段原样发给本地 Agent：

```text
请先阅读根目录 AGENTS.md、docs 下的 V1 开发文档，以及 docs/external/audio/ 下新增的全部音频 API 文档。

目标：在不改变上层业务接口和 generation 状态机语义的前提下，实现真实 AI Audio Provider。

要求：
1. 不猜测文档未说明的字段、鉴权或回调行为；遇到歧义先在 docs/API_AUDIO_PROVIDER_MAPPING.md 标注。
2. 将供应商状态完整映射到内部 AudioStatus。
3. 使用现有 AiAudioProvider 接口；如确实缺少能力，只做向后兼容的最小接口扩展。
4. 所有 secret 从环境变量读取。
5. 对请求和响应日志脱敏。
6. 如果供应商结果 URL 会过期，READY 后立刻转存到 StorageService。
7. 为 submit/query/error mapping 增加测试。
8. 完成后运行后端测试、启动 smoke test，并更新 .env.example 与 RUNBOOK。
```

---

## 38. 可直接给本地 Agent 的项目启动指令

```text
你现在负责实现 future-self-mini V1。

第一优先级：严格阅读 AGENTS.md 和 docs 中的 V1 实施文档。
不要一次性完成全部功能。按 Phase 0 → Phase 9 顺序执行，每完成一个 Phase：
- 运行编译与测试；
- 修复所有失败；
- 更新文档；
- 输出本阶段完成项、测试结果、遗留项；
然后再进入下一 Phase。

架构约束：
- Java 21 + Spring Boot 4.1.x；
- MySQL 8 是事实源；
- Redis 仅做缓存/限流/锁/幂等；
- 单体应用；
- 所有第三方 AI 通过 adapter；
- 音频 API 文档未提供之前只实现 MockAiAudioProvider 和完整业务框架，禁止猜测真实 API；
- 数据库结构只通过 Flyway migration 变更；
- 不实现 P1/P2 范围。

从 Phase 0 开始。
```

---

## 39. V1 最终验收场景

必须完整演示以下主链路：

```text
1. 新用户打开小程序。
2. 登录成功。
3. 填写昵称和当前生活资料。
4. 输入：“我希望一年后拥有自己的设计工作室，收入更稳定，有更多自由时间。”
5. 系统生成 1～3 个补充问题。
6. 用户回答后点击生成。
7. 页面显示生成进度。
8. 文本 Story 先完成并可阅读。
9. 页面显示“AI 音频生成中”。
10. 音频完成后出现播放器并能播放。
11. 用户收藏 Story。
12. 返回首页可看到 Story 和今日 Affirmations。
13. 用户退出后再次进入，数据仍在。
14. 管理后台能找到该用户、Goal、Story、Task、AI 日志。
15. 人工让 Audio Provider 模拟失败，后台可重试并最终完成。
```

只要上述场景稳定，V1 就达到可内测标准。

---

## 40. 后续迭代顺序建议

V1 内测数据验证后，按以下顺序考虑，而不是同时开发：

1. 音频风格/音色选择。
2. 分享卡片。
3. 微信服务通知。
4. 连续使用与轻习惯系统。
5. 重新生成与多版本故事。
6. 支付/会员。
7. 更高级的人生档案和长期记忆。

是否进入下一项，以真实用户留存和播放数据决定。

---

## 附录 A：推荐 `.env.example`

```dotenv
# MySQL
DB_URL=jdbc:mysql://localhost:3306/future_self?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
DB_USERNAME=root
DB_PASSWORD=

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# WeChat
WECHAT_APP_ID=
WECHAT_APP_SECRET=
WECHAT_MOCK_LOGIN=true

# Text AI
AI_TEXT_PROVIDER=mock
AI_TEXT_BASE_URL=
AI_TEXT_API_KEY=
AI_TEXT_MODEL=

# Audio AI
AI_AUDIO_PROVIDER=mock
AI_AUDIO_BASE_URL=
AI_AUDIO_API_KEY=
AI_AUDIO_VOICE_ID=

# Storage
STORAGE_PROVIDER=local
STORAGE_LOCAL_DIR=./data/media

# Admin bootstrap (local only; production should change)
ADMIN_BOOTSTRAP_USERNAME=admin
ADMIN_BOOTSTRAP_PASSWORD=
```

---

## 附录 B：本地开发 README 最低要求

README 至少包含：

- 项目目标。
- 技术栈。
- 目录结构。
- MySQL 建库方式。
- 环境变量。
- 后端启动命令。
- admin-web 启动命令。
- 小程序导入方式。
- Mock AI 模式说明。
- 如何放置音频 API 文档。
- 如何切换真实 Audio Provider。
- 测试命令。
- 常见问题。

---

## 附录 C：技术版本说明

文档编写日（2026-11-16）Spring 官方稳定版本包含 Spring Boot 4.1.2；其系统要求为 Java 17 或更高。因此本项目推荐 Java 21 + Spring Boot 4.1.x。若实际开始开发时项目初始化器提供了更新的 4.1.x patch，可在不改变大版本的情况下升级 patch 版本；不要自动切换到 RC/SNAPSHOT。

参考：

- https://docs.spring.io/spring-boot/
- https://docs.spring.io/spring-boot/system-requirements.html

---

---

## 41. 小程序视觉设计规范（Stella-inspired）

> 本节为 V1 的强制视觉实现规范。目标是让用户第一眼感受到与 Stella 相近的“梦境、女性化、时尚杂志、轻奢、安静、沉浸”气质，同时适配微信小程序的中文阅读、屏幕尺寸和系统能力。允许借鉴公开可见的布局逻辑、色彩气质、圆角、卡片层级和交互机制；禁止直接复制 Stella 的 Logo、品牌名、原始照片、插画、文案、音频、图标文件或逐像素复刻其受保护素材。

### 41.1 视觉目标与关键词

整体视觉关键词：

- `Editorial`：像时尚杂志/生活方式杂志，而不是工具型 SaaS。
- `Soft Pink`：大面积奶油粉、雾粉、浅杏粉作为背景，而不是高饱和“少女粉”。
- `Dreamy`：通过柔和渐变、轻微光晕、照片暗角、半透明层制造梦境感。
- `Minimal`：界面元素少，留白多，边框和阴影克制。
- `Serif Headline`：标题使用高对比衬线字体气质，正文使用系统无衬线字体保证中文可读性。
- `Personal`：首页直接出现用户昵称、愿望与“未来画面”，避免通用工具感。
- `Immersive Audio`：音频播放页应像“进入一个未来场景”，而不是普通音乐播放器。

禁止出现以下风格：

- 大面积科技蓝/紫色赛博渐变。
- 复杂玻璃拟态、强发光边框、霓虹效果。
- 大量粗黑体、大面积高饱和按钮。
- 工具 App 常见的密集宫格、复杂数据卡片。
- 过度卡通化插画或二次元元素。

### 41.2 Design Tokens

Agent 必须在 `miniapp/styles/tokens.wxss` 中集中定义以下设计变量，不允许页面散落大量硬编码色值。

#### 41.2.1 色彩

| Token | 建议值 | 用途 |
|---|---:|---|
| `--color-bg` | `#F8E9E8` | 全局主背景，柔和奶油粉 |
| `--color-bg-soft` | `#FDF4F0` | 内容页浅背景 |
| `--color-surface` | `#FFF9F6` | 卡片/输入框底色 |
| `--color-surface-pink` | `#F3D8DF` | 次级粉色卡片 |
| `--color-surface-lilac` | `#E9DFF3` | 少量薰衣草色故事卡片 |
| `--color-text` | `#1E1A1C` | 主文字，接近黑但不纯黑 |
| `--color-text-secondary` | `#6F6468` | 次级文字 |
| `--color-text-tertiary` | `#9C8F94` | 提示文字 |
| `--color-accent` | `#184AA5` | 少量高对比蓝，用于强调标题/链接，不作为全局主按钮 |
| `--color-heart` | `#C85D72` | 收藏/爱心 |
| `--color-line` | `rgba(30,26,28,.10)` | 细分隔线 |
| `--color-overlay` | `rgba(20,15,18,.34)` | 图片上的暗色遮罩 |

允许在设计评审后对上述色值做 ±5% 的视觉调整，但必须保持“低饱和粉色背景 + 深色正文 + 极少蓝色强调”的关系。

#### 41.2.2 渐变

仅允许使用低对比、柔和渐变：

```css
--gradient-page: linear-gradient(180deg, #F9E7E7 0%, #F8ECE7 55%, #F4DFE6 100%);
--gradient-card-pink: linear-gradient(145deg, #F7D7E2 0%, #FCE9E4 100%);
--gradient-card-lilac: linear-gradient(145deg, #E7DCF3 0%, #F5E7EF 100%);
--gradient-orb: radial-gradient(circle at 35% 30%, #FFF8FF 0%, #E7C5EA 35%, #D5A8C8 70%, #C58EB2 100%);
```

不要使用纯黑到透明的硬渐变；图片底部遮罩采用 3 段柔和暗化，保证文字可读。

#### 41.2.3 圆角

| Token | 值 | 用途 |
|---|---:|---|
| `--radius-xs` | `16rpx` | 小标签 |
| `--radius-sm` | `24rpx` | 小卡片 |
| `--radius-md` | `32rpx` | 输入框/普通卡片 |
| `--radius-lg` | `40rpx` | 主要故事卡片 |
| `--radius-pill` | `999rpx` | 胶囊按钮/筛选项 |

主要卡片不要全部使用相同圆角。故事主卡片优先 36～44rpx，输入控件 28～34rpx。

#### 41.2.4 阴影

Stella 风格应“几乎看不到阴影，但能感到层级”。建议：

```css
--shadow-card: 0 12rpx 36rpx rgba(71, 46, 58, .08);
--shadow-floating: 0 20rpx 60rpx rgba(57, 35, 46, .14);
```

不要使用明显灰黑投影或多层 Material Design 阴影。

### 41.3 字体与排版

#### 41.3.1 字体策略

中文正文使用系统字体：

```css
font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", Arial, sans-serif;
```

大标题、故事标题、Affirmation 卡片可使用衬线风格：

```css
font-family: "Songti SC", "STSong", "Noto Serif SC", serif;
```

V1 不强依赖远程字体，避免首屏加载和授权问题。如果后续确定具备合法授权的字体，可通过 `wx.loadFontFace` 替换标题字体；Agent 不得自行下载或打包来源不明的商业字体。

#### 41.3.2 字号层级

| 类型 | 建议字号 | 字重 | 行高 |
|---|---:|---:|---:|
| Hero 标题 | 56～68rpx | 400 | 1.05～1.15 |
| 页面标题 | 42～48rpx | 400 | 1.15 |
| 卡片标题 | 32～38rpx | 400/500 | 1.2 |
| 正文 | 28～30rpx | 400 | 1.55～1.7 |
| 辅助文字 | 24～26rpx | 400 | 1.4 |
| 标签 | 22～24rpx | 500 | 1.2 |

中文衬线标题不要用粗体 700；优先使用字重 400～500 体现杂志感。

### 41.4 页面基础布局

- 页面左右安全边距：`32rpx`，大屏可提升到 `40rpx`。
- 首屏顶部与胶囊按钮安全区保持至少 `24rpx` 视觉间隔。
- 模块垂直间距以 `24 / 32 / 48 / 64rpx` 为主。
- 页面内容最大视觉宽度始终与设备宽度绑定，不做 PC 式窄容器。
- 卡片之间优先使用留白分隔，少用实线。
- 所有主页面使用渐变/纯粉背景，不使用纯白整页背景。

### 41.5 核心组件规范

#### 41.5.1 顶部品牌区 `brand-header`

结构：

```text
[产品文字 Logo / 名称]                    [♡] [历史/设置图标]
```

要求：

- 产品名使用衬线体，30～36rpx。
- 不复制 Stella 字标；项目必须使用自己的名称。
- 右侧图标使用 64～72rpx 圆形浅色底，线性图标 28～32rpx。
- 图标颜色统一 `--color-text`，避免彩色图标。

#### 41.5.2 Manifest 输入 `manifest-input`

这是首页最重要的交互组件，视觉上参考 Stella 的“胶囊输入 + 圆形发送按钮”。

```text
┌─────────────────────────────────────┐
│  输入你此刻最想实现的事……       ●  │
└─────────────────────────────────────┘
```

规范：

- 高度 88～96rpx。
- 背景 `--color-surface`。
- 圆角 `--radius-pill`。
- 左右内边距 28～32rpx。
- 右侧提交按钮 64～68rpx 深色圆形，使用箭头/星形抽象图标。
- 聚焦状态仅轻微提升阴影，不出现高饱和描边。

#### 41.5.3 Trending/快捷愿望胶囊

- 横向滚动，不换行。
- 每项高度 52～60rpx。
- 浅透明背景 + 1rpx 低对比描边。
- 文案 22～24rpx。
- 点击后填充到主输入框，而不是立即提交。

#### 41.5.4 故事卡片 `story-card`

Stella 的核心视觉是“照片/渐变 + 未来场景标题 + 轻量播放按钮”。V1 建议支持两种卡片：

**A. Photo Card**

- 比例 0.78～0.85（竖向）。
- 顶部为场景图，底部使用柔和遮罩。
- 标题放在图片下部，衬线体白字。
- 左下/右下放圆形播放按钮。
- 收藏按钮使用空心爱心。

**B. Gradient Card**

- 无合适图片时使用粉/薰衣草渐变。
- 中心可放 1 个柔和 Orb。
- 标题使用深色衬线体。
- 不使用随机 AI 图片作为 V1 强依赖，避免画面不一致。

#### 41.5.5 “Future Self” 横向 Carousel

首页与生成完成页可使用卡片横向轮播：

```text
      [前一张露出] [当前主卡] [后一张露出]
```

要求：

- 当前卡宽度约屏幕 68～76%。
- 前后卡各露出 24～40rpx，告诉用户可以滑动。
- 滑动停止时做 120～180ms 的轻微缩放回弹。
- 不做复杂 3D 透视。

#### 41.5.6 Affirmation 卡片 `affirmation-card`

视觉参考“叠放纸张/卡片堆”，而不是普通列表。

```text
╭────────────────────────────╮
│ ♡                        ↗ │
│                            │
│     我正在自然地成为       │
│     我理想中的自己。       │
│                            │
│          今日肯定语         │
╰────────────────────────────╯
   ╰──────────────────────╯
      ╰────────────────╯
```

- 主卡背景 `#FFF9F6`。
- 卡片下方用 2 层浅粉/浅紫伪卡片制造堆叠。
- 主文案衬线体 34～42rpx，居中，行高 1.45。
- 左上收藏、右上分享/更多为轻量线性图标。
- 支持左右滑：右滑收藏，左滑下一条；V1 必须同时提供可点击按钮，避免用户不知道手势。
- 滑动时最大旋转不超过 4deg。

#### 41.5.7 Glowing Orb `dream-orb`

可作为 Onboarding、生成中、播放器的统一视觉母题。

- 直径 120～220rpx，根据场景变化。
- 使用 CSS/rpx 渐变与阴影实现，不依赖位图。
- 生成中做 3～4 秒一次缓慢呼吸动画。
- 不做快速闪烁。

#### 41.5.8 Mini Audio Player

当用户离开故事详情但音频仍在播放时，页面底部显示固定迷你播放器：

```text
[小圆图/Orb]  未来的一个普通早晨      [Ⅱ]
             02:18 / 04:06
```

- 高度 112～128rpx。
- 背景 `rgba(255,249,246,.94)`。
- 16～24rpx 圆角。
- 上方 2rpx 进度条。
- 底部与自定义 Tab Bar 之间留 12～16rpx。

#### 41.5.9 主按钮

主按钮不采用亮蓝色。默认：

- 深炭黑背景 `#211C1F`。
- 白/奶油白文字。
- 高度 92rpx。
- 胶囊圆角。
- 按下缩放 `0.985`，透明度降低至 0.9。

蓝色只用于强调文字、少量链接或特定 editorial 标题。

### 41.6 页面级视觉规范

#### 41.6.1 启动页 / 首次欢迎页

视觉目标：像一本“未来人生杂志”的封面。

```text
                  ○  柔和 Orb

             遇见未来的自己

       把你想要的人生说给我听。
       我会把它写成一个已经发生的故事。

              [ 开始我的故事 ]
```

- 背景使用 `--gradient-page`。
- Hero 标题使用衬线体 58～64rpx。
- Orb 位于标题上方或背景中上方。
- 主按钮固定在底部安全区上方 48～64rpx。
- 页面禁止放大量产品说明。

#### 41.6.2 Onboarding

Stella 的 onboarding 体验接近“对话式问卷”，本项目也按单问题单页实现。

每页结构：

```text
[←]                         2 / 6

          小标题 / 提示

       你希望我怎么称呼你？

      [      小雨         ]


                 [继续]
```

规范：

- 一屏只问一个核心问题。
- 顶部进度不要使用粗进度条；使用细线或 `2 / 6` 数字。
- 问题标题衬线体 44～52rpx。
- 输入框使用大尺寸卡片/下划线式输入，避免企业表单风。
- 选项用大号圆角胶囊或 2 列软卡片。
- AI 动态追问页显示 80～120rpx Orb，强化“Stella-like conversational guide”感，但使用自己的品牌名称。

#### 41.6.3 首页 Home

首页必须优先呈现“今天”和“我想要什么”，而不是功能菜单。

推荐布局：

```text
Future Self                                   ♡  ◷

           [头像/Orb]

        小雨，你想实现什么？

[ 输入你此刻最想实现的事……             ● ]

热门愿望  [事业更自由] [理想爱情] [旅行] →

For you                                  查看全部
┌─────────────┐  ┌─────────────┐
│ 图片/渐变    │  │ 图片/渐变    │
│ 未来故事标题  │  │ 未来故事标题  │
│         ▶    │  │         ▶    │
└─────────────┘  └─────────────┘

Today's affirmation
[叠放式肯定语卡片]
```

实现要求：

- 首屏尽量在无需滚动时看到输入框和至少一部分 For You 卡片。
- 顶部欢迎标题不要写成“功能中心”。
- `For you`、`Today` 等小节标题可以保留少量英文增强 editorial 气质，但核心操作必须中文可理解。
- 首页不要放统计数据、连续天数、复杂入口。

#### 41.6.4 创建新愿望

采用底部 Sheet 或独立沉浸页，不采用复杂表单。

视觉结构：

- 顶部小标题：“What do you want?” / “你想让什么成为现实？”
- 中央大文本输入区域 220～320rpx。
- 下方 4～8 个灵感胶囊。
- 底部深色胶囊按钮“生成我的未来”。
- 若内容较长，输入框高度自适应，不出现多层边框。

#### 41.6.5 AI 生成中

禁止仅显示系统 `loading` 转圈。

使用统一生成页：

```text
             ◉
       （缓慢呼吸 Orb）

        正在写下你的未来…

     我正在把你刚刚说的细节
       编进一个属于你的故事。
```

可以轮换 2～4 条短提示，但不显示虚假百分比。后端只有真实阶段状态时才显示：

- 正在理解愿望
- 正在写故事
- 正在生成声音
- 正在准备播放

#### 41.6.6 故事详情 / AI 音频播放器

这是整个产品视觉权重最高的页面。

推荐两层结构：

**上半屏：沉浸封面**

- 场景图或柔和渐变背景占 45～58vh。
- 顶部透明返回、收藏、更多。
- 标题位于画面下部。
- 图片上叠 `--color-overlay`，保证白字清晰。

**下半屏：播放器**

```text
未来的一个普通早晨
为你生成 · AI 内容

00:48 ━━━━━━━●━━━━━━━━ 04:12

      ↺15        ▶        15↻

[雨声] [咖啡馆] [海浪] [无环境音]

阅读故事全文  ↓
```

要求：

- 播放按钮直径 104～120rpx，深色圆形。
- 进度条细 4～6rpx，拖动点 18～22rpx。
- 环境音采用横向胶囊选择，不做复杂混音台。
- AI 音频 API 如果直接返回带背景音的成品，则隐藏环境音层控件；如果供应商支持独立旁白，应保留背景音叠加能力。
- 文本全文可从下方展开，正文采用舒适长文排版（30rpx / 1.7）。

#### 41.6.7 我的故事 / Library

- 页面标题使用衬线体大字：“我的未来”或“Library”。
- 顶部提供 `全部 / 收藏` 两个轻量筛选。
- 内容优先 2 列卡片网格；单卡片宽约 `(屏宽 - 32*2 - 20) / 2`。
- 卡片封面可用渐变 + 场景关键词，不强制为每条故事生成图片。
- 最近播放可单独横向一行，减少大列表的工具感。

#### 41.6.8 Affirmations 页面

如果 V1 将 Affirmation 做独立详情：

- 页面顶部仅标题 + 收藏入口。
- 主体中央一张 80～86vw 的叠卡。
- 背景保持粉色渐变，不出现列表。
- 底部提示“向右收藏 · 向左下一条”。
- 轻微 haptic 反馈（支持时）。

#### 41.6.9 我的 / Profile

Profile 不需要 Stella 式视觉完全沉浸，应保持简洁：

- 顶部昵称 + 1 个柔和 Orb/抽象头像。
- 菜单采用大块浅色列表，不用传统灰色系统 Setting 列表。
- Life Profile、故事收藏、设置为 3～5 个入口即可。
- 页面整体仍用 `--color-bg-soft`，不切换纯白风格。

### 41.7 图片与视觉素材策略

V1 不要求 AI 为每个故事生成封面图，否则会引入额外成本、审核和风格漂移。

推荐素材优先级：

1. 自有/已授权的生活方式摄影图库（城市、咖啡馆、海边、家居、旅行等）。
2. 设计系统内置 12～20 张经过统一调色的场景图。
3. 纯渐变 + Orb 作为兜底。
4. 后续版本再评估 AI 图片生成封面。

统一调色：

- 降低饱和度 8～18%。
- 略增暖色。
- 黑位不要过深。
- 避免过度商业广告感、摆拍感。
- 图片上有文字时统一增加柔和暗遮罩。

Agent 不得直接抓取 Stella App Store 截图作为产品素材。

### 41.8 动效规范

动效只服务于“柔和、沉浸”，不炫技。

| 动效 | 时长 | 建议 |
|---|---:|---|
| 页面元素淡入 | 220～320ms | opacity + translateY 8～12rpx |
| 卡片点击 | 100～140ms | scale 0.985 |
| Carousel 回弹 | 140～200ms | ease-out |
| Orb 呼吸 | 3000～4500ms | scale 0.96→1.03 + opacity |
| Affirmation 滑卡 | 180～260ms | translateX + rotate ≤4deg |
| Bottom Sheet | 260～340ms | cubic-bezier(0.22,1,0.36,1) |

必须尊重性能：低端安卓机上禁止大面积实时 blur。半透明效果优先用预设 rgba 背景，而不是 `backdrop-filter`。

### 41.9 微信小程序适配要求

Stella 是原生 App，小程序必须做以下适配：

- 使用 `navigationStyle: custom` 的页面必须自行处理状态栏与微信胶囊安全区。
- 通过 `wx.getWindowInfo()` / `wx.getMenuButtonBoundingClientRect()` 计算顶部安全距离，不写死 iPhone 尺寸。
- 底部按钮/播放器必须考虑 `safe-area-inset-bottom`。
- 若采用自定义 Tab Bar，需实现 `custom-tab-bar/`，只保留“首页 / 我的”。
- 小程序页面栈较深时，必须保留明确返回入口；不能完全照搬 iOS 手势返回。
- 图片统一使用 WebP/JPEG 压缩，首页首屏图片单张建议控制在 200KB 左右，避免粉色大图导致首屏慢。
- 自定义字体若启用，必须有系统字体 fallback，字体加载失败不能破坏排版。

### 41.10 小程序目录新增规范

在原目录基础上增加：

```text
miniapp/
  styles/
    tokens.wxss              # 色彩、间距、圆角、阴影 token
    typography.wxss          # 排版工具类
    animations.wxss          # 呼吸、淡入、滑卡等
  components/
    brand-header/
    dream-orb/
    manifest-input/
    pill-chip/
    story-card/
    story-carousel/
    affirmation-card/
    audio-player/
    mini-player/
    primary-button/
    soft-sheet/
    empty-state/
  custom-tab-bar/
```

每个组件都必须有：

- `.wxml`
- `.wxss`
- `.ts`
- `.json`
- 最低限度的组件使用说明（放在 `miniapp/components/README.md` 或组件目录注释中）。

### 41.11 Design Token 示例

Agent 可按以下方向创建 `tokens.wxss`：

```css
page {
  --color-bg: #F8E9E8;
  --color-bg-soft: #FDF4F0;
  --color-surface: #FFF9F6;
  --color-surface-pink: #F3D8DF;
  --color-surface-lilac: #E9DFF3;
  --color-text: #1E1A1C;
  --color-text-secondary: #6F6468;
  --color-text-tertiary: #9C8F94;
  --color-accent: #184AA5;
  --color-heart: #C85D72;
  --color-line: rgba(30, 26, 28, .10);

  --radius-xs: 16rpx;
  --radius-sm: 24rpx;
  --radius-md: 32rpx;
  --radius-lg: 40rpx;
  --radius-pill: 999rpx;

  --shadow-card: 0 12rpx 36rpx rgba(71, 46, 58, .08);
  --shadow-floating: 0 20rpx 60rpx rgba(57, 35, 46, .14);

  background: linear-gradient(180deg, #F9E7E7 0%, #F8ECE7 55%, #F4DFE6 100%);
  color: var(--color-text);
}
```

### 41.12 可访问性与易用性

“复刻视觉”不得牺牲基本可用性：

- 正文与背景对比度必须保证正常阅读。
- 任何仅靠手势完成的操作都必须提供按钮替代。
- 触控目标最小建议 80×80rpx。
- 长标题最大 2～3 行，超出后使用渐进展开，不直接截成难懂内容。
- 音频播放状态必须同时有图形和文字/时间反馈。
- 重要文案不得直接压在无暗层的复杂照片上。

---

## 42. 视觉开发任务与 Agent 验收标准

### 42.1 UI Phase 0 — 先搭设计系统，不直接堆页面

Agent 在实现正式业务页面前必须先完成：

1. `tokens.wxss`。
2. `typography.wxss`。
3. `animations.wxss`。
4. `dream-orb`。
5. `manifest-input`。
6. `pill-chip`。
7. `story-card`。
8. `affirmation-card`。
9. `primary-button`。
10. `audio-player` 的静态视觉骨架。

验收：建立一个本地 `pages/dev/design-system` 页面，仅开发环境可访问，用于一次性展示所有组件状态：default / pressed / disabled / loading / long-text。

### 42.2 UI Phase 1 — Onboarding

必须完成：

- 欢迎页。
- 单问题式 onboarding。
- 进度显示。
- 选项胶囊。
- 文本输入。
- AI 追问页。
- Orb 呼吸动画。

视觉验收：

- 不出现传统后台表单感。
- 任一屏核心问题不超过一个。
- 主标题视觉权重明显高于说明文字。
- 375px 逻辑宽度和常见安卓宽屏均无溢出。

### 42.3 UI Phase 2 — Home

必须完成：

- 自定义顶部品牌区。
- Manifest 胶囊输入。
- Trending 横向 chips。
- For You 横向故事卡。
- Today's Affirmation 叠卡。
- Mini Player 占位。

视觉验收：

- 第一屏的视觉重点依次为：用户/问题 → 输入框 → 个性化故事。
- 不得把“我的故事、设置、资料”等入口堆到首屏。
- 背景必须保持连续的柔和粉色/奶油色，不出现明显白色断层。

### 42.4 UI Phase 3 — Story + Audio

必须完成：

- 沉浸式封面。
- 标题 + AI 标识。
- 音频进度与主播放按钮。
- 环境音 chips（若真实音频 Provider 能力允许）。
- 故事全文展开。
- 收藏。
- Mini Player 与页面内播放器状态同步。

视觉验收：

- 用户进入后首先感受到“一个未来场景”，其次才是播放器控件。
- 播放器不得看起来像系统默认 `<audio>`。
- 在音频尚未生成时，文字故事依然有完整的高级展示状态，而不是错误/空白页。

### 42.5 UI Phase 4 — Library + Profile

必须完成：

- 两列故事网格。
- 收藏筛选。
- 空状态。
- Profile 卡片入口。
- 设置页保持相同视觉系统。

### 42.6 Agent 截图验收要求

本地 Agent 每完成一个 UI Phase，必须在微信开发者工具中至少保存以下截图用于人工确认：

1. iPhone 375px 级别宽度首页。
2. 典型 Android 412px 级别宽度首页。
3. Onboarding 文本输入态（键盘弹起前后的布局）。
4. Story Player 播放态。
5. Affirmation 滑卡静止态。
6. Library 长标题场景。

若 Agent 不具备自动截图能力，则必须在任务结束报告中明确列出需要人工打开的页面和视觉验收点，不得声称“UI 已完全还原”而没有实际预览。

### 42.7 视觉一致性检查清单

提交 UI 任务前逐项确认：

- [ ] 页面主背景使用统一 token。
- [ ] 主标题采用衬线气质，正文采用系统无衬线。
- [ ] 没有页面私自新增高饱和主色。
- [ ] 卡片圆角、阴影来自 token。
- [ ] 所有图标风格一致（线性、细笔画）。
- [ ] 首页首屏无功能宫格。
- [ ] 生成中使用 Orb，不只用 loading spinner。
- [ ] Affirmation 是卡片体验，不是普通列表。
- [ ] Story 页面具有沉浸封面和自定义播放器。
- [ ] AI 生成内容有可见但不突兀的标识。
- [ ] 小程序胶囊按钮/状态栏/底部安全区均正确适配。
- [ ] 未使用 Stella 的 Logo、截图、原图、原文案或未授权字体。

### 42.8 视觉验收优先级

如果开发时间有限，视觉优先级必须是：

```text
P0：Onboarding → Home → Story/Audio Player
P1：Affirmation → Library
P2：Profile / Settings 的细节精修
```

第一版宁愿减少次要页面的装饰，也必须把 Home 和 Story Player 做到接近 Stella 的整体氛围。

---

**文档结束。**
