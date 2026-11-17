<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { adminApi } from "../services/adminApi";

// 用户详情（§20.2）：基础信息 + Life Profile（仅供排查标注）+ 目标/故事/最近任务
const route = useRoute();
const userId = Number(route.params.id);
const detail = ref<Record<string, any> | null>(null);
const loading = ref(true);

onMounted(async () => {
  try {
    detail.value = await adminApi.userDetail(userId);
  } finally {
    loading.value = false;
  }
});

function rowsOf(json: string | null): string {
  if (!json) return "—";
  try {
    return JSON.stringify(JSON.parse(json), null, 2);
  } catch {
    return json;
  }
}
</script>

<template>
  <div v-loading="loading">
    <template v-if="detail">
      <el-card>
        <template #header>基础信息</template>
        <p>昵称：{{ (detail.user as any).displayName }}（ID {{ (detail.user as any).id }}）</p>
        <p>状态：{{ (detail.user as any).status }} · 角色：{{ (detail.user as any).role }}</p>
        <p>Onboarding：{{ (detail.user as any).onboardingCompleted ? "完成" : "未完成" }}</p>
        <p>openid（脱敏）：{{ (detail.user as any).openidMasked }}</p>
      </el-card>

      <el-card style="margin-top: 16px">
        <template #header>Life Profile（仅供内测排查，注意敏感信息）</template>
        <pre class="json">{{ rowsOf(JSON.stringify(detail.lifeProfile)) }}</pre>
      </el-card>

      <el-card style="margin-top: 16px">
        <template #header>目标（{{ (detail.goals as any[]).length }}）</template>
        <el-table :data="detail.goals as any[]" size="small">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="category" label="分类" width="120" />
          <el-table-column prop="title" label="标题" />
          <el-table-column prop="status" label="状态" width="100" />
        </el-table>
      </el-card>

      <el-card style="margin-top: 16px">
        <template #header>故事（{{ (detail.stories as any[]).length }}）</template>
        <el-table :data="detail.stories as any[]" size="small">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="title" label="标题" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="moderationStatus" label="审核" width="100" />
          <el-table-column prop="provider" label="Provider" width="100" />
        </el-table>
      </el-card>

      <el-card style="margin-top: 16px">
        <template #header>最近任务与错误（{{ (detail.recentTasks as any[]).length }}）</template>
        <el-table :data="detail.recentTasks as any[]" size="small">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="status" label="状态" width="140" />
          <el-table-column prop="retryCount" label="重试" width="70" />
          <el-table-column prop="errorStage" label="错误阶段" width="110" />
          <el-table-column prop="errorCode" label="错误码" width="180" />
          <el-table-column prop="errorMessage" label="错误信息" />
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.json { white-space: pre-wrap; word-break: break-all; font-size: 12px; color: #555; }
</style>
