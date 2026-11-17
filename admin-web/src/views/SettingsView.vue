<script setup lang="ts">
import PageHeader from "../components/PageHeader.vue";
import StatusTag from "../components/StatusTag.vue";

const sections = [
  {
    title: "安全",
    items: [
      "管理员 token 与用户 token 隔离，所有 /admin/** 接口需 ADMIN 角色",
      "管理端不展示用户完整 openid，列表中一律脱敏",
      "Prompt 与用户档案属于敏感信息，仅限内测排查使用",
    ],
  },
  {
    title: "AI 供应商",
    items: [
      "文本：DeepSeek（OpenAI 兼容协议）· 模型 deepseek-chat",
      "音频：腾讯云 TTS（基础同步 + 150 字分段拼接）· 音色 501004 月华",
      "存储：阿里云 OSS（heyi2 / oss-cn-hangzhou），对象公网可读",
    ],
  },
  {
    title: "限流与保留",
    items: [
      "目标创建 30 次/小时 · 追问 20 次/小时 · 故事生成 10 次/天 · 音频重试 5 次/天",
      "已删除故事的音频保留 30 天后由清理任务物理删除（每日 03:05）",
      "生成任务最多自动重试 3 次，超过后进入 FAILED 等待人工处理",
    ],
  },
];

const envs = [
  { key: "AI_TEXT_PROVIDER", desc: "mock / openai", value: "openai" },
  { key: "AI_AUDIO_PROVIDER", desc: "mock / tencent / tencent-sync", value: "tencent-sync" },
  { key: "STORAGE_PROVIDER", desc: "local / oss", value: "oss" },
  { key: "WECHAT_MOCK_LOGIN", desc: "开发期静默登录", value: "true" },
];
</script>

<template>
  <div>
    <PageHeader title="设置" subtitle="当前配置以只读方式展示；修改请编辑本地 .env 并重启后端" />

    <el-alert
      title="此页面为配置速览，不是在线编辑器：变更环境变量后需重启后端生效。"
      type="info"
      :closable="false"
      show-icon
      class="alert"
    />

    <el-row :gutter="16">
      <el-col v-for="s in sections" :key="s.title" :xs="24" :md="8">
        <el-card shadow="never" class="card">
          <template #header><span class="card-title">{{ s.title }}</span></template>
          <ul class="list">
            <li v-for="(item, i) in s.items" :key="i">{{ item }}</li>
          </ul>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="card">
      <template #header><span class="card-title">当前生效的关键配置</span></template>
      <el-table :data="envs" size="small" stripe>
        <el-table-column prop="key" label="环境变量" width="240">
          <template #default="{ row }"><code class="code">{{ row.key }}</code></template>
        </el-table-column>
        <el-table-column prop="desc" label="可选值" min-width="260" />
        <el-table-column prop="value" label="当前值" width="180">
          <template #default="{ row }"><StatusTag :label="row.value" type="primary" effect="plain" /></template>
        </el-table-column>
      </el-table>
      <div class="hint">完整变量说明见仓库 <code class="code">docs/RUNBOOK.md</code> 与 <code class="code">.env.example</code>。</div>
    </el-card>
  </div>
</template>

<style scoped>
.card {
  border-radius: 12px;
  border: 1px solid #ebeef5;
  margin-bottom: 16px;
}
.alert {
  border-radius: 10px;
  margin-bottom: 16px;
}
.card-title {
  font-weight: 600;
  font-size: 14px;
}
.list {
  margin: 0;
  padding-left: 18px;
  color: #4b5563;
  font-size: 13px;
  line-height: 1.9;
}
.code {
  background: #f5f7fa;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  color: #4f46e5;
}
.hint {
  margin-top: 12px;
  font-size: 12px;
  color: #909399;
}
</style>
