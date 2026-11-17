<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
import { Refresh, Search } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { adminApi } from "../services/adminApi";
import type { AdminTask, PageData } from "../services/adminApi";
import PageHeader from "../components/PageHeader.vue";
import StatusTag from "../components/StatusTag.vue";
import { fmtDateTime } from "../utils/format";
import { TASK_STATUS_OPTIONS, taskStatus } from "../utils/labels";

const data = ref<PageData<AdminTask>>({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
const status = ref("");
const userId = ref("");
const pageSize = ref(20);
const loading = ref(false);
const currentPage = ref(1);
const autoRefresh = ref(false);
let timer: number | null = null;

const RUNNING = [
  "PENDING",
  "TEXT_GENERATING",
  "TEXT_MODERATING",
  "TEXT_READY",
  "AUDIO_SUBMITTING",
  "AUDIO_PROCESSING",
  "AUDIO_STORING",
  "RETRY_WAIT",
];

function retryable(row: AdminTask): boolean {
  return (row.status === "FAILED" || row.status === "COMPLETED") && RUNNING.indexOf(row.status) < 0;
}

async function load(page = 0) {
  loading.value = true;
  currentPage.value = page + 1;
  try {
    const params: Record<string, unknown> = { page, size: pageSize.value };
    if (status.value) {
      params.status = status.value;
    }
    if (userId.value.trim()) {
      params.userId = Number(userId.value.trim());
    }
    data.value = await adminApi.tasks(params);
  } catch (e) {
    ElMessage.error("加载任务列表失败");
  } finally {
    loading.value = false;
  }
}

function reset() {
  status.value = "";
  userId.value = "";
  load(0);
}

function toggleAuto(value: boolean) {
  if (timer) {
    window.clearInterval(timer);
    timer = null;
  }
  if (value) {
    timer = window.setInterval(() => load(data.value.number), 5000);
  }
}

async function onRetry(row: AdminTask) {
  const label = taskStatus(row.status).label;
  await ElMessageBox.confirm(
    `任务 #${row.id}（${label}）将重置重试预算并重新推进；若文本已生成则只重试音频。确定继续？`,
    "手动重试",
    { confirmButtonText: "重试", cancelButtonText: "取消", type: "warning" },
  );
  const result = await adminApi.retryTask(row.id);
  ElMessage.success(`已重置为「${taskStatus(result.status).label}」，后台 worker 将继续推进`);
  load(data.value.number);
}

onMounted(() => load());
onBeforeUnmount(() => {
  if (timer) {
    window.clearInterval(timer);
  }
});
</script>

<template>
  <div>
    <PageHeader title="生成任务" subtitle="定位失败任务并手动重试；文本完成后仅重试音频">
      <template #actions>
        <el-switch v-model="autoRefresh" active-text="自动刷新(5s)" @change="toggleAuto" />
        <el-button :icon="Refresh" :loading="loading" @click="load(data.number)">刷新</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="card">
      <div class="toolbar">
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 160px" @change="load(0)">
          <el-option v-for="opt in TASK_STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-input
          v-model="userId"
          placeholder="按用户 ID 过滤"
          :prefix-icon="Search"
          clearable
          style="width: 200px"
          @keyup.enter="load(0)"
          @clear="load(0)"
        />
        <el-button type="primary" @click="load(0)">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <span class="total">共 {{ data.totalElements }} 个任务</span>
      </div>

      <el-table :data="data.content" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="userId" label="用户" width="80" />
        <el-table-column prop="storyId" label="故事" width="80">
          <template #default="{ row }">{{ row.storyId ?? "—" }}</template>
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <StatusTag :label="taskStatus(row.status).label" :type="taskStatus(row.status).type" />
          </template>
        </el-table-column>
        <el-table-column label="进度" width="110">
          <template #default="{ row }">
            <el-progress :percentage="row.progress || 0" :stroke-width="6" :show-text="false" />
          </template>
        </el-table-column>
        <el-table-column prop="retryCount" label="重试" width="70" />
        <el-table-column prop="errorStage" label="错误阶段" width="100">
          <template #default="{ row }">{{ row.errorStage || "—" }}</template>
        </el-table-column>
        <el-table-column label="错误信息" min-width="240">
          <template #default="{ row }">
            <el-tooltip v-if="row.errorCode" :content="row.errorMessage || row.errorCode" placement="top">
              <span class="error-code">{{ row.errorCode }}</span>
            </el-tooltip>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="warning"
              plain
              :disabled="!retryable(row)"
              @click="onRetry(row)"
            >
              重试
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="data.totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="(p: number) => load(p - 1)"
          @size-change="() => load(0)"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.card {
  border-radius: 12px;
  border: 1px solid #ebeef5;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}
.total {
  margin-left: auto;
  font-size: 12px;
  color: #909399;
}
.error-code {
  color: #ef4444;
  cursor: help;
  font-size: 12px;
}
.muted {
  color: #c0c4cc;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
