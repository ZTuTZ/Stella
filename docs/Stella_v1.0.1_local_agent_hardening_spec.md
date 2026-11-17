# Stella V1.0.1 Hardening — 本地 Agent 修复实施文档

> 目标仓库：`https://github.com/ZTuTZ/Stella`  
> 基准分支：`main`  
> 本文审查基准：提交 `6d97d0cca3c0a75fea61c5184e665024004d7b6d`  
> 文档用途：直接交给本地开发 Agent 执行。  
> 修复目标：不扩展产品范围，优先修复正确性、任务状态机、隐私安全、并发幂等、生产配置和工程质量问题，使 V1 适合 10～50 人内测，并为后续公开发布消除结构性隐患。

---

## 0. Agent 执行总则

### 0.1 开工前必须执行

1. 阅读根目录 `AGENTS.md`。
2. 阅读 `future-self-mini_local-agent-development-spec_v1.1.md`。
3. 阅读 `docs/README.md`、`docs/RUNBOOK.md`、`docs/API_V1.md`、`docs/DATABASE_V1.md`、`docs/PHASE9_CHECKLIST.md`。
4. 执行 `git status`，确认工作区状态。
5. 执行 `git log -5 --oneline`，确认当前 HEAD。
6. 如果当前 HEAD 已晚于本文基准提交，先检查相关文件 diff；**不得覆盖用户在本文之后新增的修改**，应在当前代码基础上合并修复。
7. 修复前先跑一遍现有测试，记录基线结果。

### 0.2 禁止事项

- 不得为了修复问题改成微服务。
- 不得更换 Spring Boot / Vue / 微信原生技术栈。
- 不得引入若依等重型脚手架。
- 不得把 Redis 改成业务事实源。
- 不得把 AI 调用放入数据库长事务。
- 不得猜测第三方 API、OSS、腾讯云、微信或内容安全服务的未确认参数。
- 不得修改已经执行过的 `V1__init_schema.sql`、`V2__seed_prompts.sql`；数据库结构变化必须创建新 migration。
- 不得删除现有测试来“修复测试”。
- 不得在日志、数据库调试字段、前端代码中加入 secret/token/session_key/API key。
- 不得借本次 hardening 新增支付、会员、社区、签到等产品功能。
- 不得把“以后上线再处理”的占位能力伪装成已经可用的正式功能。

### 0.3 每个任务的完成规则

每个独立修复完成后至少执行：

```bash
cd backend && mvn test
cd ../admin-web && npm ci && npm run build
cd ../miniapp && npm ci && npx tsc --noEmit
```

如修改只涉及单端，可以先跑对应测试；但进入最终提交前必须跑完整检查。

每个任务建议独立 commit，格式：

```text
fix: ...
security: ...
test: ...
chore: ...
docs: ...
```

最终不得只提交代码而不更新相关文档。

---

# 1. 修复范围和优先级

## 1.1 内测前必须完成（P0 / P1）

| ID | 优先级 | 问题 | 目标 |
|---|---|---|---|
| H-01 | P0 | 首页 Affirmation 失败会导致 NPE / 500 | Affirmation 故障不影响首页主数据 |
| H-02 | P0 | “每小时限流”实际按分钟重置 | 恢复真正的小时固定窗口，并补测试 |
| H-03 | P0 | 用户私人音频使用永久公开 URL | 生产改为私有对象 + 短时访问 URL；本地媒体仅 dev/test |
| H-04 | P1 | Storage 失败会重新提交 TTS | Storage 重试只重试转存，不重复付费生成 |
| H-05 | P1 | 新音频尚未 READY 就淘汰旧音频 | 新音频成功后再原子切换旧音频 |
| H-06 | P1 | Generation 幂等与并发存在竞态 | DB 做最终幂等兜底，Redis 只做优化 |
| H-07 | P1 | AI 调用日志仍可能保存私人正文 | 日志只留元数据/hash/长度，不留正文片段 |
| H-08 | P1 | AudioTransferService 存在 SSRF/任意 file 读取面 | 限制 scheme、host、file 目录 |
| H-09 | P1 | Profile / followup 最终上下文缺统一审核 | 最终 AI 上下文调用前再做一次 moderation |
| H-10 | P1 | 生产配置容易误用 local/mock | 增加 prod profile + 启动 fail-fast 校验 |
| H-11 | P1 | Admin 登录无失败限流 | 增加登录尝试限制，避免暴力破解 |
| H-12 | P1 | 无 CI，现有测试不能自动阻止回归 | GitHub Actions 自动跑后端/后台/小程序检查 |

## 1.2 内测可以带着走，但公开发布前必须完成

| ID | 问题 |
|---|---|
| R-01 | 内容安全仍为关键词兜底，公开发布前需接真实 Moderation Provider；无正式供应商文档时禁止猜接口 |
| R-02 | 用户注销目前只做部分软删除，不等于彻底删除/匿名化；公开发布前补合规删除链路 |
| R-03 | 生成内容显式/隐式标识、音频 metadata、最终服务备案/登记要求按上线时适用规则核验 |
| R-04 | Worker 当前只保证单实例安全；公开扩容前必须实现可证明的多实例 claim/lock |
| R-05 | 生产域名、HTTPS、小程序合法域名、OSS CORS/私有读策略必须完成实机验收 |

## 1.3 本次不做

- 不实现新的付费体系。
- 不新增社交功能。
- 不新增“最近播放”完整产品功能；当前占位入口应隐藏。
- 不实现复杂推荐算法。
- 不为了多实例提前拆队列中间件。
- 不在没有供应商文档时实现假的远程内容安全 Provider。

---

# 2. H-01：首页 Affirmation 故障隔离

## 2.1 当前问题

文件：

`backend/src/main/java/com/futureself/home/HomeController.java`

当前逻辑意图是：Affirmation 生成失败时不拖垮首页。

但异常分支把 `affirmations` 设为 `null`，随后仍调用：

```java
affirmations.date()
affirmations.items()
```

因此 Affirmation AI 或其依赖失败时，首页仍可能 NPE 并返回 500。

## 2.2 修复要求

推荐行为：返回空的 AffirmationBrief，而不是让整个字段为 null。

建议语义：

```text
GET /api/v1/home
HTTP 200

user             正常
todayStory       正常
recentStories    正常
affirmations     非 null
  date           当天日期
  items          []
hasRunningTask   正常
```

实现要求：

1. catch 中记录一条 `warn`，只记录异常类型/错误码，不记录用户 prompt 正文。
2. fallback 使用当前业务时区的当天日期和空列表。
3. 不允许因为 Affirmation 故障影响故事、用户、任务状态聚合。
4. 前端无需额外特殊判断；`items=[]` 时自然不显示 affirmation 卡即可。

## 2.3 测试要求

新增失败场景测试：

- 让 `AffirmationService.today()` 抛出运行时异常或业务异常。
- 请求 `/api/v1/home`。
- 断言 HTTP 200。
- 断言 `todayStory/recentStories/user/hasRunningTask` 仍正常。
- 断言 `affirmations.items` 为 `[]`。
- 不允许出现 500。

## 2.4 验收标准

- [ ] Affirmation 正常时现有行为不变。
- [ ] Affirmation 异常时 `/home` 仍返回 200。
- [ ] 后端测试覆盖异常分支。
- [ ] 日志没有用户正文。

---

# 3. H-02：修正 Hourly Rate Limit

## 3.1 当前问题

文件：

`backend/src/main/java/com/futureself/common/ratelimit/RateLimitService.java`

当前 `allowHourly()` 使用：

```java
DateTimeFormatter.ofPattern("yyyyMMddHHmm")
```

这意味着 key 每分钟变化一次。

配置虽然写的是：

```text
Goal create: 30 / hour
Followup:     20 / hour
```

实际效果却近似：

```text
30 / minute
20 / minute
```

## 3.2 修复要求

V1.0.1 不要求上复杂滑动窗口，先实现可靠的**固定小时窗口**。

最小方案：

```java
DateTimeFormatter.ofPattern("yyyyMMddHH")
```

同时：

1. 将类注释中的“滑窗”改成“固定窗口”，避免文档与实现不一致。
2. 明确业务时区，推荐统一使用 `Asia/Shanghai`，不要依赖服务器默认时区。
3. 日窗口也应使用同一业务时区。
4. TTL 保留窗口冗余即可。
5. Redis 故障时保持当前“限流降级放行”的策略，但必须有 warn。

可选优化：引入可注入 `Clock`，提高时间相关测试可测性；如果引入，必须保持实现轻量。

## 3.3 测试要求

至少覆盖：

- 同一个小时窗口内，第 N 次允许，第 N+1 次拒绝。
- daily limit 行为不回归。
- Redis 异常时返回 allow=true，并记录降级日志。

若引入 Clock，再增加：

- 跨小时后重新允许。

## 3.4 额外修复

`GenerationTaskService.create()` 当前在幂等查找之前先消耗 daily quota。

要求调整顺序：

```text
1. 先解析已有 idempotency key
2. 如果已存在同一任务，直接返回
3. 只有真正要创建新任务时才消耗 rate limit
```

同一个客户端重试请求不得重复消耗日配额。

---

# 4. H-03：私人音频访问改为短时授权 URL

## 4.1 当前问题

相关文件：

- `backend/src/main/java/com/futureself/config/SecurityConfig.java`
- `backend/src/main/java/com/futureself/storage/MediaController.java`
- `backend/src/main/java/com/futureself/storage/StorageService.java`
- `backend/src/main/java/com/futureself/storage/LocalStorageService.java`
- `backend/src/main/java/com/futureself/storage/oss/OssStorageService.java`
- `backend/src/main/java/com/futureself/story/StoryService.java`
- `backend/src/main/java/com/futureself/audio/domain/AudioAsset.java`

当前：

- `/media/**` 全局 permitAll。
- OSS 返回长期公网对象地址。
- `audio_asset.public_url` 可长期保存可访问 URL。

Future Self 的音频可能包含昵称、亲密关系、目标、家庭、财富愿望等私人内容，因此“不容易猜到 URL”不能作为权限控制。

## 4.2 目标架构

生产环境：

```text
private OSS object
      ↓
storage_key 持久化
      ↓
用户请求 story detail
      ↓
后端 owner 校验已通过
      ↓
StorageService 生成 5~30 分钟短时读取 URL
      ↓
返回给小程序播放
```

数据库中不再依赖永久公网 URL。

## 4.3 实现要求

### A. StorageService

在现有接口基础上增加“生成读取地址”的能力，例如：

```java
String createReadUrl(String key, Duration ttl)
```

命名可调整，但语义必须明确：

- `save()` 只负责保存并返回 key/元数据。
- “URL”是访问层概念，不应默认为永久公开 URL。

### B. OSS

- 正式环境 bucket 预期为 private。
- 读取 URL 应是带有效期签名的 URL。
- **必须依据已存在的 OSS 官方文档/项目文档实现，不得猜签名规则。**
- 如果当前 docs 中缺少生成签名 URL 所需资料，则先把接口、业务层和 local 实现改好，并在 OSS 实现处留下明确阻塞说明；不要伪造签名。

### C. Local

`/media/**` 只作为本地开发/test 能力。

推荐：

- `MediaController` 仅在 local/test profile 注册；或采用等价的 profile 条件。
- prod profile 不注册本地媒体 controller。
- SecurityConfig 即使保留 `/media/**` permitAll，也不能在 prod 存在实际映射。

### D. StoryService

`audioOf()`：

- 读取 `storageKey`。
- 每次返回 story detail 时生成短时 URL。
- 不直接返回 DB 中长期 `publicUrl`。

### E. provider URL

第三方 `provider_url` 只用于转存。

转存成功后：

- 推荐清空 `providerUrl`，减少临时供应商 URL 长期驻留数据库。
- 保留 provider task id 和必要审计元数据即可。

### F. DB

`public_url` 当前已 nullable，本任务原则上不需要修改表结构。

不要为了删字段去改 V1 migration。

如果未来决定移除列，另开 migration，不属于本轮强制项。

## 4.4 测试要求

- local/test 模式仍可播放本地音频。
- prod storage 逻辑不会返回永久公开 URL。
- story owner 校验不回归。
- 没有 storageKey 时 audio.url 为 null。
- URL 生成失败时不得泄露对象内部路径之外的信息。

## 4.5 验收标准

- [ ] 生产不依赖公开 bucket。
- [ ] DB 不再把永久 URL 当事实源。
- [ ] story response 的 URL 有明确短期有效期语义。
- [ ] providerUrl 转存成功后可清理。
- [ ] local/test 体验不受影响。

---

# 5. H-04：Storage 失败只重试 Storage

## 5.1 当前问题

文件：

`backend/src/main/java/com/futureself/generation/GenerationOrchestrator.java`

当前失败阶段映射大致为：

```text
STAGE_AUDIO   -> AUDIO_SUBMITTING
STAGE_STORAGE -> AUDIO_SUBMITTING
```

结果是：

```text
腾讯已经生成成功
→ provider URL 已拿到
→ OSS 转存偶发失败
→ RETRY_WAIT
→ 再次提交腾讯 TTS
```

这会重复付费并增加延迟。

## 5.2 正确状态机

应改成：

```text
TEXT / AI failure
    -> TEXT_GENERATING

MODERATION transient failure
    -> TEXT_MODERATING

AUDIO submit/query failure
    -> AUDIO_SUBMITTING

STORAGE failure
    -> AUDIO_STORING
```

Storage retry 必须复用同一个 `audio_asset.provider_url`。

## 5.3 Asset 状态要求

不要在第一次临时 Storage 故障时立即把 asset 永久标记为 `FAILED`。

推荐：

- 可重试期间保持可识别的在途状态。
- 当 task 的 storage retry 真正耗尽后，再把当前 asset 标成 `FAILED`。

如果不新增 `STORING` asset status，可继续使用 `PROCESSING` 作为在途状态，但必须在代码注释写清。

## 5.4 测试要求

新增一个可计数的 fake/mock：

```text
Audio provider submit count = 1
Storage 第一次失败
Storage 第二次成功
最终 task = COMPLETED
Audio provider submit count 仍然 = 1
```

同时测试：

- provider_url 缺失时正确进入失败处理。
- storage 重试耗尽后 task=FAILED，asset=FAILED。

---

# 6. H-05：音频重生成使用 Blue-Green 切换

## 6.1 当前问题

文件：

- `GenerationOrchestrator.java`
- `AudioAssetRepository.java`
- `StoryService.java`
- `AudioCleanupJob.java`

当前新音频 submit 时就会把旧有效音频改为 `SUPERSEDED`。

如果新一轮生成失败，用户原来能听的音频也会消失。

## 6.2 正确行为

```text
旧音频 READY
     ↓ 保持可播放
新音频 SUBMITTED/PROCESSING/STORING
     ↓
新音频真正 READY
     ↓
同一事务中：
    旧音频 -> SUPERSEDED
    新音频 -> READY
```

这就是本项目的 blue-green asset replacement。

## 6.3 Story API 选择规则

生成新音频期间：

1. 如果存在 `READY` 的旧音频，优先返回可播放 READY 音频。
2. 如果没有任何 READY 音频，再返回最新的 PENDING/SUBMITTED/PROCESSING/FAILED 状态供 UI 展示。

不要使用“最新一条非 SUPERSEDED”直接覆盖旧 READY 的可播放能力。

需要增加明确 repository 查询，例如：

```text
find latest READY
find latest non-SUPERSEDED
```

具体方法名由 Agent 按 Spring Data 风格确定。

## 6.4 SUPERSEDED 文件清理

当前 `AudioCleanupJob` 对 `SUPERSEDED` asset 直接 skip，可能导致重生成产生的旧文件永久占用存储。

修复要求：

- 被 supersede 的旧文件应进入可配置 retention 清理。
- 不要在数据库事务里同步删除 OSS 文件。
- 建议定时任务按 `updated_at + retention` 删除对象，删除成功后清空 storageKey/publicUrl。
- 删除失败保留 key，下次继续尝试。

如果已有 `deletedAudioDays`，可复用或新增更语义化配置；不要过度设计。

## 6.5 测试要求

- 有旧 READY，启动 retry，新音频 PROCESSING：story detail 仍返回旧 READY URL。
- 新音频失败：旧 READY 仍可播放。
- 新音频成功：新 READY 生效，旧 asset 变 SUPERSEDED。
- superseded cleanup 可回收旧 storage object。

---

# 7. H-06：生成任务持久幂等 + 并发保护

## 7.1 当前问题

`GenerationTaskService.create()` 目前主要依赖：

```text
Redis GET idempotency key
DB existsByGoalIdAndStatusNotIn
INSERT task
Redis SET idempotency key
```

存在两个问题：

1. 两个并发请求可能同时通过 `exists` 后都 INSERT。
2. Redis 暂时不可用时，幂等逻辑可能直接影响核心创建流程；与“Redis 不是事实源”的设计原则不完全一致。

## 7.2 数据库持久幂等

建议创建新 migration，例如：

```text
V3__generation_task_idempotency.sql
```

**禁止修改 V1。**

推荐增加：

```sql
idempotency_key VARCHAR(128) NULL
UNIQUE KEY uk_task_user_idempotency (user_id, idempotency_key)
```

注意 MySQL UNIQUE 对 NULL 可允许多行，正好支持没有 key 的旧调用。

Entity 同步增加字段。

## 7.3 创建顺序

推荐：

```text
1. normalize idempotency key（trim + length validation）
2. DB 查询 userId + idempotencyKey
3. 已存在 -> 直接返回已有 task，不消耗限流
4. 真正新建 -> rate limit
5. 对 goal 获取数据库级并发保护
6. 再检查是否有 active task
7. INSERT generation_task
8. 唯一键冲突 -> 回查并返回已有 task
9. Redis 仅做缓存加速，可用则写，不可用则 warn 后继续
```

## 7.4 同一 Goal 的不同 key 并发

仅有 idempotency unique 不能阻止：

```text
key=A
key=B
同一个 goal
```

同时创建两个 active task。

推荐使用**数据库 pessimistic lock 锁定 goal 行**：

```text
SELECT manifestation_goal ... FOR UPDATE
```

在同一个短事务里完成：

```text
lock goal
check active task
insert generation_task
commit
```

不要用长事务包住 AI 调用；这里事务只覆盖创建检查和 INSERT。

## 7.5 Redis 降级

所有 idempotency Redis get/set 都要 catch Redis 异常：

- Redis 正常：缓存加速。
- Redis 异常：DB 仍可正确创建和幂等。
- 不得因为 Redis 宕机导致故事创建接口整体不可用。

## 7.6 测试要求

并发测试至少覆盖：

1. 同一 user + 同一 key + 同一 goal，并发 N 个请求 -> 只产生 1 条 task，所有成功调用返回同一个 taskId。
2. 同一 goal + 两个不同 key 并发 -> 最多产生 1 个 active task；另一个得到 `TASK_ALREADY_RUNNING` 或等价受控结果。
3. Redis idempotency 操作异常 -> DB 路径仍可用。
4. 重放同一 idempotency key 不重复消耗 daily quota。

---

# 8. H-07：AI 日志隐私收敛

## 8.1 当前问题

重点文件：

- `FollowupService.java`
- `GenerationOrchestrator.java`
- `AffirmationService.java`
- `OpenAiCompatibleTextAiProvider.java`
- `AiCallLogService.java`

当前部分“脱敏摘要”实际是 prompt 前 200 字，仍可能直接包含私人信息。

另外 `OpenAiCompatibleTextAiProvider` 在 JSON 解析失败时会记录生成内容前若干字符，也可能包含私人故事正文。

## 8.2 新规则

生产日志和 `ai_call_log` 默认不得保存：

- currentSituation 原文
- importantPeople 名称/关系/备注原文
- goalDescription 原文
- followup answer 原文
- story content 原文
- provider 临时 URL
- token/session_key/secret/API key

允许保存：

```text
operation
promptTemplateId / promptKey / version
provider
model
inputCharCount
outputCharCount
inputTokens
outputTokens
latencyMs
success
errorCode
errorMessage（过滤、截断）
contextHash（可选 SHA-256）
requestId
taskId/userId（内部 ID）
```

## 8.3 实现建议

增加统一 helper，例如：

```text
AiLogSanitizer
AiDiagnosticSummaryFactory
```

名称自定。

不要在多个 Service 各自手写“截前 200 字”。

`OpenAiCompatibleTextAiProvider` JSON 解析失败时改为日志：

```text
invalid json: chars=xxx hash=xxxx
```

不要打印正文。

## 8.4 测试要求

构造含以下敏感 marker 的 prompt：

```text
SECRET_MARKER_123
PRIVATE_PERSON_NAME
```

执行 AI 日志写入后：

- 查询 `ai_call_log`。
- 断言任何 summary/error/log payload 中均不包含 marker。
- 仍能看到长度/hash/operation 等诊断字段。

---

# 9. H-08：AudioTransferService SSRF / file 读取防护

## 9.1 当前问题

文件：

`backend/src/main/java/com/futureself/generation/AudioTransferService.java`

当前可直接打开：

```text
file:...
http://...
https://...
```

如果上游 provider 返回异常/恶意 URL，存在读取本机文件或请求内网地址的风险。

## 9.2 file: 规则

`file:` 只允许读取**受控目录**：

- mock fixture 目录
- `tencent-sync` 的 sync output 目录

必须：

```text
normalize absolute path
resolved.startsWith(allowedBaseDir)
```

禁止：

```text
file:///etc/passwd
../
软链接越界（尽量通过 real path 校验）
任意本机路径
```

## 9.3 http(s) 规则

生产：

- 默认只允许 `https`。
- 网络下载 host 必须属于配置的 allowlist。
- allowlist 应来自环境变量/配置，不要在代码中猜腾讯 COS 域名。
- 解析后的目标不得是 loopback、link-local、site-local/private 内网地址。
- 当前 Java HttpClient 默认不跟随 redirect 时保持不跟随；未来如开启 redirect，每次跳转都必须重新校验。

建议增加配置：

```text
AI_AUDIO_RESULT_HOST_ALLOWLIST=
```

格式可以是逗号分隔 host/suffix，但必须有清晰的匹配规则和测试。

在真实腾讯环境中，允许的域名值由实际已验证文档/响应确定。

## 9.4 测试要求

必须拒绝：

- `file:///etc/passwd`
- 越界 `file:` 路径
- `http://127.0.0.1/...`
- `http://169.254.169.254/...`
- 非 allowlist HTTPS host

必须允许：

- mock fixture 受控目录
- tencent-sync output 受控目录
- 配置在 allowlist 中的 HTTPS host

---

# 10. H-09：最终 Prompt 上下文统一审核

## 10.1 当前问题

Goal title/description 会 moderation，但以下内容也会进入最终 AI prompt：

- Life Profile currentSituation
- importantPeople
- preferences
- followup answers

这些字段目前没有统一的“最终上下文”审核。

## 10.2 修复要求

不要在每个字段保存接口里重复堆关键词判断。

推荐在真正发给模型之前：

```text
PromptContextAssembler
      ↓
最终 user context / userPrompt
      ↓
ModerationProvider.check(...)
      ↓
TextAiProvider
```

至少覆盖：

- FOLLOWUP_INPUT
- STORY_INPUT
- AFFIRMATION_INPUT

可以继续使用现有 `RuleBasedModerationProvider` 作为内测实现。

如果审核拦截：

- 返回受控业务错误。
- 不调用真实 LLM。
- 不记录正文到日志。

## 10.3 注意

本次**不要**在没有正式内容安全供应商文档的情况下伪造远程接口。

真实 Moderation Provider 属于公开发布 gate，接口结构可沿用现有 `ModerationProvider`。

## 10.4 测试要求

将高风险内容放到：

- profile.currentSituation
- importantPeople.note
- followup answer

确保最终 STORY/FOLLOWUP/AFFIRMATION AI 调用前能被拦截，并验证 TextAiProvider 未被调用。

---

# 11. H-10：生产 Profile 与 Fail-Fast 配置

## 11.1 当前问题

当前默认：

```yaml
spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}
```

并且 Text/Audio provider 默认可能为 mock，storage 默认 local。

如果部署时漏配环境变量，服务可能以开发配置启动。

## 11.2 新增 application-prod.yml

创建：

```text
backend/src/main/resources/application-prod.yml
```

要求：

- 不显示 health 组件详细信息。
- 不启用 local media controller。
- 日志级别不应默认 DEBUG。
- 生产配置所有 secret 仍来自环境变量。

## 11.3 ProductionConfigurationValidator

新增轻量启动校验（例如 `@Profile("prod")` 的 `ApplicationRunner`）。

prod 下以下情况必须直接启动失败：

```text
WECHAT_MOCK_LOGIN=true
AI_TEXT_PROVIDER=mock
AI_AUDIO_PROVIDER=mock
STORAGE_PROVIDER=local
WECHAT_APP_ID 为空
WECHAT_APP_SECRET 为空
真实 Text AI 所需 baseUrl/apiKey/model 缺失
真实 Audio provider 所需关键配置缺失
OSS bucket/credentials 缺失
ADMIN_BOOTSTRAP_PASSWORD 仍为空（若生产需要 bootstrap）
```

具体 provider 的 required fields 应按当前已实现 provider 判断，不要泛化猜测。

## 11.4 小程序 API 地址

文件：

`miniapp/services/config.ts`

不得继续只有：

```ts
export const API_BASE_URL = "http://localhost:8080";
```

要求：

- 按 `envVersion` 至少区分 `development / trial / release`。
- development 可默认 localhost。
- trial/release 不得悄悄 fallback 到 localhost。
- 当前真实域名未知时，用明确的空配置 + 启动/请求时错误提示，不得编造域名。
- 所有 API 地址只在一个配置入口维护。

## 11.5 验收标准

- [ ] prod + mock provider -> 启动失败。
- [ ] prod + local storage -> 启动失败。
- [ ] local 开发不受影响。
- [ ] 小程序 release 不可能静默请求 localhost。

---

# 12. H-11：Admin 登录限流

## 12.1 当前问题

文件：

`backend/src/main/java/com/futureself/admin/api/AdminAuthController.java`

目前 BCrypt 校验正确，但登录接口没有失败次数限制。

## 12.2 最小方案

增加 Redis 登录尝试限制：

```text
维度：IP + usernameHash
窗口：例如 10~15 分钟
阈值：例如 5~10 次失败
```

不要把原始密码、完整 username 放进日志。

成功登录后可清理对应失败计数。

阈值可配置，不要写散落 magic number。

如服务部署在反向代理后，不要未经配置盲目信任任意 `X-Forwarded-For`；默认使用 servlet 解析后的 remote address，或按项目部署文档配置可信代理。

## 12.3 Redis 故障策略

内测可允许降级，但必须：

- 记录 warn。
- 登录本身仍需正常 BCrypt 验证。
- RUNBOOK 说明 admin 应优先通过内网/VPN/反向代理访问。

## 12.4 测试

- 连续错误密码达到阈值后被限制。
- 正确密码在未锁定时可登录。
- 登录成功后失败计数清理或自然过期。
- 密码值不进入日志。

---

# 13. H-12：GitHub Actions CI

## 13.1 新增 workflow

建议：

```text
.github/workflows/ci.yml
```

触发：

```text
push main
pull_request
```

## 13.2 CI 环境

Service：

```text
MySQL 8
Redis
```

Runtime：

```text
JDK 21
Maven 3.9+
Node 22
```

## 13.3 CI jobs

### backend

```bash
cd backend
mvn test
```

确保测试库配置可在 CI 创建/使用。

### admin-web

```bash
cd admin-web
npm ci
npm run build
```

### miniapp

```bash
cd miniapp
npm ci
npx tsc --noEmit
```

### 基础敏感信息扫描

至少增加仓库级检查，阻止提交：

```text
.env
真实 PEM/key 文件
典型 access key / secret 变量赋真实值
```

优先使用简单、可维护的检查；如引入第三方 action，锁定明确版本。

## 13.4 验收标准

- [ ] main push 自动运行。
- [ ] 任一测试失败 workflow 失败。
- [ ] 后台 TS/build 失败 workflow 失败。
- [ ] 小程序 TypeScript 失败 workflow 失败。

---

# 14. UI / 产品清理项（P2，但建议本轮完成）

## 14.1 环境音占位功能

当前播放器显示：

```text
雨声 / 咖啡馆 / 海浪 / 无环境音
```

但点击只提示“以后生效”。

本轮不实现混音，建议**隐藏环境音选择区域**，而不是保留假交互。

不要删除未来扩展的数据结构也可以，但用户界面不应出现不可用控件。

## 14.2 最近播放占位

首页 `onHistory()` 目前只 Toast“即将上线”。

本轮不实现最近播放，要求：

- 隐藏 history 入口，或让 header 支持 `showHistory=false`。
- 不给内测用户展示死入口。

## 14.3 Story 更多操作占位

详情页 `onMore()` 目前只 Toast。

如果没有可用操作：

- 隐藏“...”按钮。

## 14.4 dev design-system 页面

`miniapp/app.json` 当前包含：

```text
pages/dev/design-system/index
```

正式包不应包含 dev 页面。

如果开发阶段已基本结束：

- 从 app.json 移除该 page 注册。
- 源码目录可保留，后续需要时再临时注册。

## 14.5 README 状态冲突

当前 README Phase 4~8 同时出现已完成 `[x]` 和未完成 `[ ]`。

要求清理成唯一事实状态，避免后续 Agent 判断错误。

---

# 15. 公开发布前 Gate（本轮可只写 TODO/Runbook，不得伪实现）

## 15.1 真实 Moderation Provider

现有接口已抽象为 `ModerationProvider`。

公开发布前：

- 依据最终供应商官方文档接真实内容安全接口。
- RuleBased 作为本地/兜底。
- 禁止根据印象猜 endpoint/header/字段。

## 15.2 用户注销与数据删除

当前 `UserDeletionService` 只：

- disable user
- nickname 脱敏
- goal/story 软删除

但 profile、openid、故事正文等仍可能保留。

公开发布前需要定义明确的：

```text
注销申请
→ token revoke
→ 账号不可登录
→ 私人内容删除/匿名化
→ 音频对象清理
→ 必要审计数据最小化保留
→ 完成状态
```

本轮不要在缺少最终合规策略的情况下做不可逆数据库大改，但要在 RUNBOOK / release checklist 标为阻塞项。

## 15.3 AI 生成内容标识

当前 UI 已有显式“AI 生成内容”文案。

公开发布前还需针对实际发布时适用的监管要求核验：

- 文本显式标识
- 音频显式标识
- 音频文件 metadata/隐式标识
- 生成服务备案/登记/公示要求

建议未来统一抽象：

```text
AiContentMarkerService
```

本轮不根据记忆直接写死监管字段。

## 15.4 Worker 多实例

当前 Worker 明确只支持单实例。

内测部署必须在 RUNBOOK 写清：

```text
backend replica = 1
```

未来需要水平扩容时再实现：

```text
DB guarded claim / SKIP LOCKED / lease timeout
```

不要现在为了“架构先进”引入 MQ。

---

# 16. 建议的提交顺序

建议严格按以下顺序执行，方便回滚和定位：

### Commit 1

```text
fix: isolate home from affirmation failures
```

内容：H-01 + 测试。

### Commit 2

```text
fix: correct hourly rate limit window
```

内容：H-02 + 测试 + 注释修正。

### Commit 3

```text
fix: make generation idempotency durable
```

内容：H-06 + 新 Flyway migration + 并发测试。

### Commit 4

```text
fix: retry audio storage without regenerating tts
```

内容：H-04 + 集成测试。

### Commit 5

```text
fix: switch regenerated audio only after ready
```

内容：H-05 + cleanup 修复 + 集成测试。

### Commit 6

```text
security: restrict media access and audio transfer urls
```

内容：H-03 + H-08。

### Commit 7

```text
security: sanitize ai diagnostics and moderate final context
```

内容：H-07 + H-09。

### Commit 8

```text
security: add production config guards and admin login throttling
```

内容：H-10 + H-11。

### Commit 9

```text
ci: add backend admin and miniapp checks
```

内容：H-12。

### Commit 10

```text
chore: remove unavailable ui entries and dev release pages
```

内容：UI P2 清理。

### Commit 11

```text
docs: refresh hardening runbook and release checklist
```

内容：README / RUNBOOK / PHASE9 / API / DATABASE 文档同步。

如果某个任务同时需要文档更新，可以在对应 commit 内同步，不必强制最后一次性更新。

---

# 17. 数据库变更要求

如果执行 H-06 增加 `generation_task.idempotency_key`：

必须创建新的 migration，例如：

```text
backend/src/main/resources/db/migration/V3__generation_task_idempotency.sql
```

要求：

- 兼容现有数据。
- 新字段 nullable。
- 不给历史 task 回填伪造 key。
- 新建唯一索引前检查设计是否允许 NULL 多行。
- `DatabaseMigrationTest` 增加 V3 验证。
- 更新 `docs/DATABASE_V1.md`（可改名/章节标注 schema v1.0.1，但不要改历史 migration）。

如果还有其他 schema 改动：

- 优先判断是否真的需要。
- 没必要不要为 hardening 增列。
- 每个 migration 保持单一逻辑目的。

---

# 18. 测试矩阵

最终至少应拥有以下自动化验证：

## Auth / Security

- [ ] 微信 mock login 正常。
- [ ] token 过期/无效返回 401。
- [ ] disabled user 返回 403。
- [ ] admin token 不可当普通用户访问资源（按现有权限模型验证）。
- [ ] admin 错误登录达到阈值被限制。

## Home

- [ ] Affirmation 正常：首页 200。
- [ ] Affirmation 异常：首页仍 200。
- [ ] running task 状态正确。

## Rate limit

- [ ] hourly 在同小时内真实生效。
- [ ] daily 生效。
- [ ] idempotent replay 不重复计数。

## Generation

- [ ] 同 key 并发只建一条 task。
- [ ] 同 goal 不同 key 并发最多一个 active task。
- [ ] Redis idempotency 故障不阻断 DB 主流程。
- [ ] TEXT retry 正常。
- [ ] AUDIO retry 正常。
- [ ] STORAGE retry 不重新调用 TTS。

## Audio replacement

- [ ] 旧 READY + 新 PROCESSING -> 旧音频仍可播放。
- [ ] 新失败 -> 旧音频仍可播放。
- [ ] 新成功 -> 新音频切换，旧变 SUPERSEDED。
- [ ] superseded storage 文件最终可清理。

## Storage / SSRF

- [ ] 任意 file 路径拒绝。
- [ ] 内网 URL 拒绝。
- [ ] 非 allowlist host 拒绝。
- [ ] 合法 provider URL 可转存。

## AI privacy

- [ ] ai_call_log 不包含构造的隐私 marker。
- [ ] invalid JSON 日志不打印模型正文。
- [ ] provider URL 不作为长期诊断日志输出。

## Moderation

- [ ] goal 输入高风险内容拦截。
- [ ] profile 中的高风险内容在最终 AI 调用前拦截。
- [ ] followup answer 中的高风险内容在最终 AI 调用前拦截。

## Frontend

- [ ] admin-web build 通过。
- [ ] miniapp TypeScript 通过。
- [ ] 无占位环境音/History/More 死入口。
- [ ] release 配置不会请求 localhost。

---

# 19. 手工回归场景

自动测试全部通过后，在微信开发者工具至少手动跑一次：

```text
1. 新用户登录
2. Onboarding
3. 填人生档案
4. 创建目标
5. AI 追问
6. 保存回答
7. 生成故事
8. 文本先出现
9. 音频处理中仍可阅读文本
10. 音频 READY 后播放
11. 收藏故事
12. 首页再次进入
13. Library 再次打开故事
14. 模拟音频 Storage 临时失败并恢复
15. retry audio 时旧音频仍能听
16. 新音频成功后切换
17. 后台定位 user / goal / story / task / AI logs
18. 后台错误密码连续尝试验证限流
```

同时手动查看日志：

- 不应出现故事正文。
- 不应出现私人 profile 内容。
- 不应出现 token/session_key/secret。

---

# 20. 最终文档同步

代码完成后必须同步：

### README.md

- 清理重复 Phase 状态。
- 写明 V1.0.1 hardening 已完成项。
- 明确 production 不能使用 mock/local storage。

### docs/RUNBOOK.md

新增：

- prod profile 启动方式。
- backend replica=1 的当前约束。
- private OSS / signed URL 配置。
- AI audio result host allowlist。
- admin 暴力破解防护。
- Redis 故障时各模块降级行为。

### docs/API_V1.md

- 说明 story audio URL 是短时 URL，不是永久 URL。
- 如果 API response shape 有变化必须同步。

### docs/DATABASE_V1.md

- 如新增 idempotency_key / V3 migration，必须记录。

### docs/PHASE9_CHECKLIST.md

重新核对，不允许保留已经被本轮发现反例推翻的“✅”描述。

尤其重新验证：

- rate limit 是否真按小时。
- private media access。
- AI 日志是否真的脱敏，而不是仅截断。
- 生产配置是否 fail-fast。

---

# 21. Definition of Done

只有以下条件全部满足，才可宣布 `v1.0.1 hardening` 完成：

- [ ] H-01 ~ H-12 全部完成，或明确写出无法完成的外部阻塞理由。
- [ ] 所有新增 bug 都有回归测试。
- [ ] 后端完整 `mvn test` 通过。
- [ ] admin-web `npm run build` 通过。
- [ ] miniapp `npx tsc --noEmit` 通过。
- [ ] GitHub Actions 通过。
- [ ] 没有修改历史 Flyway migration。
- [ ] 没有 secret/真实 key 提交到仓库。
- [ ] 用户私人音频在生产不再依赖永久公开 URL。
- [ ] Storage retry 不会重复生成 TTS。
- [ ] 音频重生成失败不会破坏旧 READY 音频。
- [ ] 同一 idempotency key 并发只产生一条 task。
- [ ] 同一 goal 不会并发产生多条 active task。
- [ ] AI 日志不保存私人正文片段。
- [ ] prod 环境无法以 mock/local 配置误启动。
- [ ] README / RUNBOOK / API / DATABASE / PHASE9 与代码一致。
- [ ] 微信开发者工具人工回归通过。

---

# 22. Agent 最终汇报格式

完成后不要只回复“已修复”。必须给出：

```text
## 完成项
- H-01 ...
- H-02 ...

## 关键设计变化
- ...

## 数据库 migration
- V3__...

## 新增/修改测试
- ...

## 验证结果
- mvn test: PASS / FAIL
- admin build: PASS / FAIL
- miniapp tsc: PASS / FAIL
- GitHub Actions: PASS / FAIL

## 未完成项 / 外部阻塞
- 例如 OSS signed URL 官方资料缺失，因此只完成接口重构，未猜测签名。

## Commit 列表
- <sha> fix: ...

## 需要人工操作
- 生产域名
- OSS private bucket 配置
- 小程序合法域名
- 最终 provider allowlist
```

不得隐瞒未通过的测试或未完成项。

---

## 附：本轮核心原则

本次 hardening 的优先级不是“再做更多功能”，而是：

```text
正确性
  > 隐私与安全
  > 幂等与故障恢复
  > 可观测性
  > 自动化回归
  > 占位 UI 清理
  > 新功能
```

Stella 当前代码已经具备完整 V1 骨架。本轮应保持现有轻量单体架构，修复真实风险，而不是重新设计项目。
