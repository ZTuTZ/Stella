<script setup lang="ts">
import { onMounted, ref } from "vue";
import { adminApi } from "../services/adminApi";

// Dashboard（§20.1）：8 项核心指标
const summary = ref<Record<string, number>>({});
const loading = ref(true);

const cards = [
  { key: "totalUsers", label: "注册用户" },
  { key: "todayActiveUsers", label: "今日活跃" },
  { key: "todayNewGoals", label: "今日新增目标" },
  { key: "todayNewStories", label: "今日生成故事" },
  { key: "audioReadyToday", label: "今日音频成功" },
  { key: "audioFailedToday", label: "今日音频失败" },
  { key: "failedTasks", label: "失败任务" },
  { key: "audioReadyTotal", label: "音频累计成功" },
];

onMounted(async () => {
  try {
    summary.value = await adminApi.dashboardSummary();
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div v-loading="loading">
    <el-row :gutter="16">
      <el-col v-for="c in cards" :key="c.key" :span="6">
        <el-card class="stat">
          <div class="stat-label">{{ c.label }}</div>
          <div class="stat-value">{{ summary[c.key] ?? "—" }}</div>
        </el-card>
      </el-col>
    </el-row>
    <el-card style="margin-top: 16px">
      <template #header>AI 平均耗时（成功调用）</template>
      <p>文本 AI：{{ summary["textAiAvgLatencyMs"] ?? "—" }} ms</p>
      <p>音频 AI：{{ summary["audioAiAvgLatencyMs"] ?? "—" }} ms</p>
      <p>音频累计失败：{{ summary["audioFailedTotal"] ?? "—" }}</p>
    </el-card>
  </div>
</template>

<style scoped>
.stat { margin-bottom: 16px; }
.stat-label { color: #888; font-size: 13px; }
.stat-value { font-size: 28px; font-weight: 600; margin-top: 8px; }
</style>
