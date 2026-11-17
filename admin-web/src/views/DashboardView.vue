<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { Refresh } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { adminApi } from "../services/adminApi";
import PageHeader from "../components/PageHeader.vue";
import StatusTag from "../components/StatusTag.vue";
import { fmtDuration, fmtValue } from "../utils/format";

const summary = ref<Record<string, number>>({});
const loading = ref(true);
const refreshedAt = ref("");

const cards = computed(() => [
  { key: "totalUsers", label: "注册用户", hint: "累计注册", color: "#6366f1", unit: "人" },
  { key: "todayActiveUsers", label: "今日活跃", hint: "最近活跃时间在今日", color: "#0ea5e9", unit: "人" },
  { key: "todayNewGoals", label: "今日新增目标", hint: "今日创建", color: "#10b981", unit: "个" },
  { key: "todayNewStories", label: "今日生成故事", hint: "今日产出", color: "#f59e0b", unit: "篇" },
]);

const audioCards = computed(() => [
  { key: "audioReadyToday", label: "今日音频成功", color: "#10b981" },
  { key: "audioFailedToday", label: "今日音频失败", color: "#ef4444" },
  { key: "failedTasks", label: "累计失败任务", color: "#f97316" },
  { key: "audioReadyTotal", label: "音频累计成功", color: "#6366f1" },
]);

async function load() {
  loading.value = true;
  try {
    summary.value = await adminApi.dashboardSummary();
    refreshedAt.value = new Date().toLocaleTimeString("zh-CN", { hour12: false });
  } catch (e) {
    ElMessage.error("加载总览失败");
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div>
    <PageHeader title="总览" subtitle="内测核心指标，数据来自业务库实时统计">
      <template #actions>
        <span v-if="refreshedAt" class="refresh-at">更新于 {{ refreshedAt }}</span>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <el-skeleton v-if="loading" :rows="6" animated />
    <template v-else>
      <el-row :gutter="16">
        <el-col v-for="c in cards" :key="c.key" :xs="24" :sm="12" :md="6">
          <div class="stat-card">
            <div class="stat-top">
              <span class="stat-label">{{ c.label }}</span>
              <span class="dot" :style="{ background: c.color }" />
            </div>
            <div class="stat-value" :style="{ color: c.color }">
              {{ fmtValue(summary[c.key]) }}<span class="unit">{{ c.unit }}</span>
            </div>
            <div class="stat-hint">{{ c.hint }}</div>
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="mt">
        <el-col v-for="c in audioCards" :key="c.key" :xs="12" :sm="6">
          <div class="mini-card">
            <span class="mini-dot" :style="{ background: c.color }" />
            <div>
              <div class="mini-value">{{ fmtValue(summary[c.key]) }}</div>
              <div class="mini-label">{{ c.label }}</div>
            </div>
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="mt">
        <el-col :xs="24" :md="12">
          <el-card shadow="never" class="card">
            <template #header>
              <span class="card-title">AI 平均耗时</span>
            </template>
            <div class="latency">
              <div class="latency-item">
                <span class="latency-label">文本模型（DeepSeek）</span>
                <span class="latency-value">{{ fmtDuration(summary["textAiAvgLatencyMs"]) }}</span>
              </div>
              <el-divider />
              <div class="latency-item">
                <span class="latency-label">音频合成（腾讯云 TTS）</span>
                <span class="latency-value">{{ fmtDuration(summary["audioAiAvgLatencyMs"]) }}</span>
              </div>
            </div>
            <div class="latency-hint">统计成功调用；数据来源 ai_call_log（TEXT / AUDIO）</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-card shadow="never" class="card">
            <template #header><span class="card-title">排障入口</span></template>
            <div class="quick-links">
              <el-button text type="primary" @click="$router.push('/tasks')">查看失败任务并重试 →</el-button>
              <el-button text type="primary" @click="$router.push('/ai-logs')">按 requestId 查 AI 调用链路 →</el-button>
              <el-button text type="primary" @click="$router.push('/prompts')">管理 Prompt 版本 →</el-button>
            </div>
            <div class="latency-hint">
              <StatusTag label="内测环境" type="info" /> 所有生成均记录 provider / model / prompt 版本，可回溯。
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.refresh-at {
  font-size: 12px;
  color: #909399;
}
.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 18px 18px 14px;
  border: 1px solid #ebeef5;
  box-shadow: 0 2px 8px rgba(31, 41, 55, 0.04);
  margin-bottom: 16px;
}
.stat-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.stat-label {
  font-size: 13px;
  color: #6b7280;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  opacity: 0.8;
}
.stat-value {
  font-size: 30px;
  font-weight: 700;
  margin-top: 8px;
  line-height: 1.1;
}
.unit {
  font-size: 12px;
  font-weight: 400;
  color: #9ca3af;
  margin-left: 4px;
}
.stat-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #b0b3bb;
}
.mini-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-radius: 12px;
  padding: 14px 16px;
  border: 1px solid #ebeef5;
  margin-bottom: 16px;
}
.mini-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.mini-value {
  font-size: 20px;
  font-weight: 600;
  line-height: 1.2;
}
.mini-label {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.mt {
  margin-top: 4px;
}
.card {
  border-radius: 12px;
  border: 1px solid #ebeef5;
  margin-bottom: 16px;
}
.card-title {
  font-weight: 600;
  font-size: 14px;
}
.latency-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0;
}
.latency-label {
  color: #6b7280;
  font-size: 13px;
}
.latency-value {
  font-size: 18px;
  font-weight: 600;
  color: #4f46e5;
}
.latency-hint {
  margin-top: 10px;
  font-size: 12px;
  color: #a8abb2;
  line-height: 1.6;
}
.quick-links {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}
</style>
