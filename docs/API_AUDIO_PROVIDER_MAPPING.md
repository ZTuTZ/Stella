# 腾讯云长文本语音合成 Provider 映射表（Phase 6）

> 依据文档（2026-11-17 抓取）：
> - 操作指引 https://cloud.tencent.com/document/product/1073/56639
> - API 概览 https://cloud.tencent.com/document/product/1073/37986
> - 基础语音合成 TextToVoice https://cloud.tencent.com/document/api/1073/37995
> - 长文本请求 CreateTtsTask https://cloud.tencent.com/document/api/1073/57373
> - 长文本结果查询 DescribeTtsTaskStatus https://cloud.tencent.com/document/api/1073/57372
> - 数据结构（状态码定义）https://cloud.tencent.com/document/api/1073/57374
> - 计费 https://cloud.tencent.com/document/product/1073/34112
> - 音色列表 https://cloud.tencent.com/document/product/1073/92668

## 1. 选型结论

选用 **长文本语音合成（异步）**：

| 对比项 | 基础语音合成 TextToVoice | 长文本 CreateTtsTask（已选用） |
|---|---|---|
| 单次文本 | 中文 150 字 / 英文 500 字母 | **10 万字符** |
| 返回 | base64 音频（同步） | TaskId + 轮询/回调 |
| 采样率 | 8k/16k/24k（部分音色） | 8k/16k |
| 免费额度 | 有（需控制台领取） | **无** |
| 结果有效期 | 无 URL | **ResultUrl 24h** |

故事正文 900~1800 字，长文本接口一次合成整篇，且与既有 `submit → query → STORAGE` 异步状态机天然匹配。

## 2. 鉴权

- 签名：**TC3-HMAC-SHA256**（京东云/腾讯云 API 3.0 标准），实现于 `TencentCloudSigner`。
- 请求域名：`tts.tencentcloudapi.com`，HTTP POST `/`，`Content-Type: application/json; charset=utf-8`。
- 请求头：`Authorization`、`Content-Type`、`X-TC-Action`、`X-TC-Version: 2019-08-23`、`X-TC-Timestamp`。
- 长文本接口 **不需要 Region**；`AI_AUDIO_TENCENT_REGION` 仅保留配置位（默认 ap-guangzhou）。
- 凭据：`AI_AUDIO_TENCENT_SECRET_ID` / `AI_AUDIO_TENCENT_SECRET_KEY`（仅环境变量/.env，不入 Git、不写日志）。
- 频率限制：提交、查询各 **20 次/秒**（API + 地域 + 子账号维度）。worker 单任务轮询间隔 ≥ 4s（详情页）/ 1.5s（worker tick），远低于限频。

## 3. 提交（submit）

`CreateTtsTask` 请求体（本项目实际发送）：

```json
{
  "Text": "<AudioScriptBuilder 处理后的旁白文本，≤100000 字符，超出截断>",
  "VoiceType": "<AI_AUDIO_VOICE_ID，数字，如 301001>",
  "Speed": 0,
  "Volume": 0,
  "SampleRate": 16000,
  "Codec": "mp3",
  "PrimaryLanguage": 1,
  "ProjectId": 0,
  "ModelType": 1
}
```

响应：`Response.Data.TaskId` → 存入 `audio_asset.provider_task_id`。

- 未配置密钥 → `AUDIO_PROVIDER_NOT_CONFIGURED`（不发起网络请求）。
- 空文本 → `AUDIO_SUBMIT_FAILED`。
- 业务错误 → 见第 5 节映射。

## 4. 查询（query）

`DescribeTtsTaskStatus` 请求体：`{"TaskId": "<provider_task_id>"}`

响应 `Response.Data` 字段与内部状态映射：

| Status | StatusStr | 内部 `AudioQueryResult.JobStatus` | 动作 |
|---|---|---|---|
| 0 | waiting | `PROCESSING` | 任务保持 `AUDIO_PROCESSING`，下轮再查 |
| 1 | doing | `PROCESSING` | 同上 |
| 2 | success | `READY`（url=`ResultUrl`，format=`mp3`） | 进入 `AUDIO_STORING`，**立即下载转存**（URL 仅 1 天有效） |
| 3 | failed | `FAILED`（errorCode=`TENCENT_TTS_TASK_FAILED`，errorMessage=`ErrorMsg`） | `audio_asset=FAILED` + 任务 RETRY_WAIT（stage=AUDIO） |
| 其他 | — | `PROCESSING`（兜底，避免任务悬挂） | 下轮再查 |

> 本项目不启用回调（`CallbackUrl`）：本地无公网回调地址，且既有 worker 轮询已满足。`capabilities.supportsWebhook=true` 仅表示供应商支持，未来可加回调入口。

## 5. 错误码映射（腾讯云 → 内部 §22）

| 腾讯云错误码（前缀） | 含义 | 内部错误码 |
|---|---|---|
| `AuthFailure.*` | 签名/密钥无效 | `AUDIO_PROVIDER_NOT_CONFIGURED` |
| `UnsupportedOperation.AuthorizationFailed/Expired` | 鉴权失败/过期 | `AUDIO_PROVIDER_NOT_CONFIGURED` |
| `UnsupportedOperation.ServerNotOpen/ServerStopped/ServerDestoryed` | 服务未开通/已停止 | `AUDIO_PROVIDER_NOT_CONFIGURED` |
| `UnsupportedOperation.NoFreeAccount/PkgExhausted/AccountArrears/NoBanlance` | 免费额度/资源包耗尽、欠费 | `AUDIO_PROVIDER_NOT_CONFIGURED` |
| `InvalidParameterValue.*` / `InvalidParameter.*` | 参数/文本非法 | 提交阶段 `AUDIO_SUBMIT_FAILED`；查询阶段 `AUDIO_PROCESSING_FAILED` |
| `LimitExceeded.AccessLimit` | 超频 | 同上 |
| `InternalError.*` / `FailedOperation.*` | 供应商内部错误 | 同上 |
| 其他/未知 | — | 同上 |

> 供应商原始 `Error.Code/Message` 只写 warn 级日志（截断 200 字符），**不返回前端**；HTTP 200 但 `Response.Error` 存在时同样视为失败。

## 6. 结果转存

`ResultUrl` 为 COS 临时链接（有效期 1 天）→ `capabilities.resultUrlMayExpire=true` → 业务层在进入 `AUDIO_STORING` 时立即经 `AudioTransferService` 下载（http(s) 分支）并写入 `StorageService`，保存 `storage_key/public_url`。音频格式 mp3、采样率 16k。

## 7. 配置

```yaml
app:
  ai:
    audio:
      provider: tencent        # mock | tencent
      voice-id: 301001         # 长文本音色列表中的大模型音色 ID
      secret-id: ${AI_AUDIO_TENCENT_SECRET_ID:}
      secret-key: ${AI_AUDIO_TENCENT_SECRET_KEY:}
      region: ${AI_AUDIO_TENCENT_REGION:ap-guangzhou}
```

切换方式：只改 `AI_AUDIO_PROVIDER`，业务层与状态机零改动（§15.5 第 4 条）。

## 8. 未决/已知约束

- **资源包**：长文本接口无免费额度；账号需在 [语音合成控制台](https://console.cloud.tencent.com/tts) 购买资源包，否则调用返回 `UnsupportedOperation.PkgExhausted`。
- 长文本音色列表与通用接口不同（精品/大模型档），`AI_AUDIO_VOICE_ID` 必须取自“长文本语音合成音色列表”；超自然大模型 2.0 仅支持通用同步接口。
- 音频时长 `durationMs` 供应商不返回，暂为 null（前端播放器以音频真实 duration 为准）。
- 契约测试：`TencentCloudAudioContractTest` 由 `AI_AUDIO_TENCENT_CONTRACT=true` 显式开启（默认跳过，避免 CI 扣费/依赖资源包）。
