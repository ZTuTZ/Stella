# RUNBOOK — 本地运行手册

> Phase 0 建立；随 Phase 更新。secret 一律走 `.env`（不进 Git）。

## 前置环境

| 依赖 | 版本 | 本机状态 |
|---|---|---|
| JDK | 21 | ✅ 21.0.12 |
| Maven | 3.9.x | ✅ `/Users/aadmin/tool/apache-maven-3.9.16`（本地仓库与 settings.xml 在该目录内） |
| MySQL | 8.x | ✅ 8.4.11（root 密码见本地 `.env`，勿外传） |
| Redis | 6/7 | ✅ 已运行（localhost:6379） |
| Node.js | ≥22（nvm 管理） | ✅ v22.22.0（`nvm alias default 22.22.0` 已设置） |
| 微信开发者工具 | 最新稳定版 | 待安装/由用户打开 |

## 初始化数据库

只需创建空库，表结构由 Flyway 自动建：

```sql
CREATE DATABASE IF NOT EXISTS future_self
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

## 配置

```bash
cp .env.example .env   # 然后填入本地 DB_PASSWORD 等非提交值
```

无 AI Key 时默认 `AI_TEXT_PROVIDER=mock`、`AI_AUDIO_PROVIDER=mock`，可完整演示（Mock 走真实数据库与状态机）。

## 启动顺序

```bash
# 1. MySQL / Redis（本机服务）
# 2. backend（端口 8080）——local profile 会自动加载项目根目录 .env，无需手动 source
/Users/aadmin/tool/apache-maven-3.9.16/bin/mvn -s scripts/maven-settings.xml -f backend/pom.xml spring-boot:run
#    或： ./scripts/dev-backend.sh
# 3. admin-web（需要时）
cd admin-web && npm install && npm run dev
# 4. 微信开发者工具打开 miniapp/
```

> 若启动报 `Access denied ... using password: NO`：说明 `.env` 未生效（不在项目根/被改坏）。
> 兜底：`set -a; source .env; set +a` 后再启动，或使用 `scripts/dev-backend.sh`。

健康检查：`GET http://localhost:8080/actuator/health`（Phase 0 起可用；DB/Redis 状态随 Phase 1 接入）。

## 测试

```bash
cd backend && mvn -s ../scripts/maven-settings.xml test          # 后端单元/集成测试（需本地 MySQL+Redis）
cd admin-web && npm run build   # 前端类型检查 + 构建
```

> 集成测试连接独立测试库 `future_self_test`（自动创建），Redis 使用 db15；需先 `source .env` 或在环境变量中提供 `DB_PASSWORD`。

## Maven 注意事项

本机全局 settings.xml 激活了云效私有仓库 profile（仅 deploy 用），其域名对 JVM TLS 握手失败会导致依赖解析中断。
**本项目构建统一使用项目级配置**：`mvn -s scripts/maven-settings.xml ...`（只走阿里云公共镜像，本地仓库路径与全局一致）。

## 对象存储（阿里云 OSS）

`STORAGE_PROVIDER=oss` 时音频上传 OSS，故事详情 `audio.url` 为 OSS 公网地址（`storage_key` 仍记录对象 key）。
`local` 模式落盘 `backend/data/media` 并经 `/media/**` 提供（MediaController 常驻，可继续播放历史本地音频）。

配置（仅本地 `.env`，禁止提交）：`OSS_ENDPOINT` / `OSS_BUCKET` / `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET`；
可选 `OSS_PUBLIC_BASE_URL` 配 CDN 或自定义域名。

实现要点（`OssStorageService`，纯 REST + OSS V1 签名，无 SDK 依赖）：
- **必须三级域名**：`https://{bucket}.{endpoint-host}/{key}`；本账号 path-style 会报 `SecondLevelDomainForbidden`；
- 该场景下 OSS 校验的 CanonicalizedResource 仍为 `/{bucket}/{key}`；
- 文件通过 `Content-MD5` 校验；`HEAD` 用于存在性/元数据；`DELETE` 支持清理任务；
- **Bucket 需公共读**（或配 CDN 公共域名）小程序才能直接播放；且需把小程序合法域名指向该域名（devtools 可临时勾选“不校验合法域名”）。
- 契约测试：`OSS_CONTRACT_TEST=true mvn test -Dtest=OssContractTest`（真实上传/HEAD/公网 GET/删除）。

## 音频 API 文档

放入 `docs/external/audio/` 后，按实施文档 §15.5 / §37 执行真实 Provider 接入。

## 真实音频（腾讯云 TTS）

两种实现，改 `AI_AUDIO_PROVIDER` 即可切换（业务层零改动）：

| 值 | 接口 | 需要 |
|---|---|---|
| `mock` | 本地静音 WAV | 无 |
| `tencent-sync` | 基础同步 `TextToVoice` + 150 字分段拼接 | **免费额度**：控制台 [资源包页](https://console.cloud.tencent.com/tts/resourcebundle) 领取（精品 800 万 / 大模型 10 万字符） |
| `tencent` | 长文本异步 `CreateTtsTask` | **付费资源包**（长文本无免费额度） |

配置：`AI_AUDIO_VOICE_ID`（大模型音色示例：`501004` 月华 / `501001` 智兰；精品：`101001` 智瑜）、`AI_AUDIO_TENCENT_SECRET_ID/KEY`（仅 .env）。
注意：音色 ID 必须取自官方音色列表；错误 ID 会因无匹配资源包报 `PkgExhausted`。
音频落盘位置：`STORAGE_LOCAL_DIR`（默认 `./data/media`，相对后端进程工作目录；`mvn -f backend/pom.xml spring-boot:run` 时为 `backend/data/media`）。
契约测试：`AI_AUDIO_TENCENT_CONTRACT=true mvn test -Dtest=TencentCloudAudioContractTest`（会真实计费，少量字符）。

## Mock 音频

`AI_AUDIO_PROVIDER=mock`（默认）时：submit 即返回模拟 taskId，`AI_AUDIO_MOCK_DELAY_MS`（默认 3000）后 READY；
READY 返回项目内 fixture（`AI_AUDIO_MOCK_FIXTURE_DIR`，启动时自动生成 2 秒静音 WAV），由后端转存到自有存储后经 `/media/**` 播放。
**真实音频 Provider 未实现**：`docs/external/audio/` 出现供应商文档前，任何非 mock 的 provider 值会因无 Bean 而启动失败（明确的配置错误）。

## 管理后台

- 凭据：`.env` 的 `ADMIN_BOOTSTRAP_USERNAME/PASSWORD`；首次启动 bootstrap 自动建 admin（BCrypt）。本地默认密码见 `.env`，**请自行修改**。
- 启动：`cd admin-web && npm run dev` → http://localhost:5173（/api 代理到 8080）。
- 能力：用户检索/封禁、目标/故事/任务列表、失败任务手动重试（音频失败只重试音频）、Prompt 版本切换、AI 日志按 requestId 排查、Dashboard 指标。

## 限流与清理

- 限流（§24，环境变量可调）：Goal 30/时、追问 20/时、生成 10/日、重试音频 5/日/故事；Redis 不可用时降级放行。
- 音频清理（§25.2）：每日 03:05 物理删除超期（默认 30 天）已删除故事的音频文件。
- 注销（§25.3）：`UserDeletionService.requestDeletion(userId)` 已预留（禁用账号+软删数据），UI 暂不开放。

## 文本 LLM（OpenAI 兼容）

默认 `AI_TEXT_PROVIDER=mock`（无 key 也能完整演示）。接真实模型只需三项：

| 变量 | DeepSeek 示例 | 说明 |
|---|---|---|
| `AI_TEXT_PROVIDER` | `openai` | 通用 OpenAI 兼容实现 |
| `AI_TEXT_BASE_URL` | `https://api.deepseek.com/v1` | 请求 `{baseUrl}/chat/completions` |
| `AI_TEXT_MODEL` | `deepseek-chat` | 记录到 story.model 与 ai_call_log |
| `AI_TEXT_API_KEY` | `sk-...` | 仅本地 .env |

其他兼容端点：阿里云百炼 `https://dashscope.aliyuncs.com/compatible-mode/v1`（qwen-plus/qwen-max）、智谱 `https://open.bigmodel.cn/api/paas/v4`（glm-4-plus）等。
要求模型支持 `response_format=json_object`；不支持时需给 Provider 加开关（当前固定发送）。
故事长度由 `prompt_template` 的 STORY 模板控制（默认 900~1800 中文字），改 Prompt 请走后台新建版本。

**结构化输出**：Provider 会把 `output_schema_json` 作为 system 消息注入模型（否则模型可能自定义字段导致解析失败，已实测踩坑）。
音频调用（`AUDIO_SUBMIT`/`AUDIO_QUERY`）同样写入 `ai_call_log`，Dashboard 可看音频平均耗时。

## Mock 微信登录

`WECHAT_MOCK_LOGIN=true`（默认 `.env` 已开）时，`POST /api/v1/auth/wechat/login` 不访问微信：
code 确定性映射为 `mock_<sha256前24位>` openid，同一 code 恒为同一用户。联调真实微信时改为 false 并配置 `WECHAT_APP_ID/WECHAT_APP_SECRET`。

## 鉴权说明

opaque token 存 Redis（key `fs:auth:token:<sha256>`，TTL 7200s）；Redis 不可用时鉴权 fail-closed（401）。

## 常见问题

- **MySQL 拒绝连接/密码错误**：检查 `.env` 的 `DB_USERNAME/DB_PASSWORD`；本机 root 密码由用户本地保存。
- **pnpm 报 Node 版本不符**：`nvm use 22`（已设 default 22.22.0）。
- **端口占用**：backend 默认 8080，admin-web dev 默认 5173。
