<script setup lang="ts">
import { onMounted, ref } from "vue";
import { adminApi } from "../services/adminApi";
import type { AdminAiLog, PageData } from "../services/adminApi";

// AI 调用日志（§23/§20）：requestId/operation/providerType 过滤，定位一次失败生成的完整链路
const data = ref<PageData<AdminAiLog>>({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
const requestId = ref("");
const operation = ref("");
const providerType = ref("");
const loading = ref(false);

async function load(page = 0) {
  loading.value = true;
  try {
    const params: Record<string, unknown> = { page, size: 20 };
    if (requestId.value) params.requestId = requestId.value;
    if (operation.value) params.operation = operation.value;
    if (providerType.value) params.providerType = providerType.value;
    data.value = await adminApi.aiLogs(params);
  } finally {
    loading.value = false;
  }
}

onMounted(() => load());
</script>

<template>
  <div>
    <el-input v-model="requestId" placeholder="requestId" style="width: 240px; margin-right: 12px" @keyup.enter="load(0)" />
    <el-input v-model="operation" placeholder="operation（FOLLOWUP/STORY/AFFIRMATION）" clearable style="width: 280px; margin-right: 12px" @keyup.enter="load(0)" />
    <el-select v-model="providerType" placeholder="类型" clearable style="width: 140px; margin-right: 12px" @change="load(0)">
      <el-option label="TEXT" value="TEXT" />
      <el-option label="AUDIO" value="AUDIO" />
      <el-option label="MODERATION" value="MODERATION" />
    </el-select>
    <el-button @click="load(0)">查询</el-button>

    <el-table :data="data.content" v-loading="loading" style="margin-top: 16px">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="requestId" label="requestId" width="200" />
      <el-table-column prop="providerType" label="类型" width="100" />
      <el-table-column prop="provider" label="Provider" width="90" />
      <el-table-column prop="operation" label="Operation" width="120" />
      <el-table-column prop="latencyMs" label="耗时(ms)" width="90" />
      <el-table-column label="结果" width="80">
        <template #default="{ row }">
          <el-tag :type="row.success ? 'success' : 'danger'">{{ row.success ? "成功" : "失败" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="inputTokens" label="In" width="70" />
      <el-table-column prop="outputTokens" label="Out" width="70" />
      <el-table-column prop="errorCode" label="错误码" width="180" />
      <el-table-column prop="errorMessage" label="错误信息" />
      <el-table-column prop="createdAt" label="时间" width="180" />
    </el-table>

    <el-pagination
      style="margin-top: 16px"
      layout="prev, pager, next, total"
      :total="data.totalElements"
      :page-size="data.size"
      :current-page="data.number + 1"
      @current-change="(p: number) => load(p - 1)"
    />
  </div>
</template>
