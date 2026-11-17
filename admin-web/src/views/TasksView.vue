<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { adminApi } from "../services/adminApi";
import type { AdminTask, PageData } from "../services/adminApi";

// 任务管理（§20.4）：状态筛选 + 手动重试（音频失败只重试音频）
const data = ref<PageData<AdminTask>>({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
const status = ref("");
const userId = ref("");
const loading = ref(false);

const STATUSES = ["PENDING", "TEXT_GENERATING", "TEXT_MODERATING", "TEXT_READY", "AUDIO_SUBMITTING",
  "AUDIO_PROCESSING", "AUDIO_STORING", "COMPLETED", "RETRY_WAIT", "FAILED", "BLOCKED"];

async function load(page = 0) {
  loading.value = true;
  try {
    const params: Record<string, unknown> = { page, size: 20 };
    if (status.value) params.status = status.value;
    if (userId.value) params.userId = Number(userId.value);
    data.value = await adminApi.tasks(params);
  } finally {
    loading.value = false;
  }
}

async function onRetry(row: AdminTask) {
  await ElMessageBox.confirm(
    `任务 #${row.id}（${row.status}）将重置重试预算并重新推进，确定？`,
    "手动重试",
  );
  const result = await adminApi.retryTask(row.id);
  ElMessage.success(`已重置为 ${result.status}，worker 将继续推进`);
  load(data.value.number);
}

onMounted(() => load());
</script>

<template>
  <div>
    <el-select v-model="status" placeholder="状态筛选" clearable style="width: 200px; margin-right: 12px" @change="load(0)">
      <el-option v-for="s in STATUSES" :key="s" :label="s" :value="s" />
    </el-select>
    <el-input v-model="userId" placeholder="按用户 ID 过滤" style="width: 180px; margin-right: 12px" @keyup.enter="load(0)" />
    <el-button @click="load(0)">查询</el-button>

    <el-table :data="data.content" v-loading="loading" style="margin-top: 16px">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="userId" label="用户" width="90" />
      <el-table-column prop="storyId" label="故事" width="90" />
      <el-table-column prop="status" label="状态" width="150">
        <template #default="{ row }">
          <el-tag :type="row.status === 'COMPLETED' ? 'success' : row.status === 'FAILED' || row.status === 'BLOCKED' ? 'danger' : 'info'">
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="progress" label="进度" width="80" />
      <el-table-column prop="retryCount" label="重试" width="70" />
      <el-table-column prop="errorStage" label="错误阶段" width="110" />
      <el-table-column prop="errorCode" label="错误码" width="180" />
      <el-table-column prop="errorMessage" label="错误信息" />
      <el-table-column label="操作" width="110">
        <template #default="{ row }">
          <el-button
            size="small"
            type="warning"
            :disabled="['COMPLETED', 'FAILED', 'BLOCKED'].indexOf(row.status) < 0 || row.status === 'BLOCKED'"
            @click="onRetry(row)"
          >
            重试
          </el-button>
        </template>
      </el-table-column>
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
