<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
import { DocumentCopy, Refresh, Search } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { adminApi } from "../services/adminApi";
import type { AdminAiLog, PageData } from "../services/adminApi";
import PageHeader from "../components/PageHeader.vue";
import StatusTag from "../components/StatusTag.vue";
import { fmtDateTime, fmtDuration } from "../utils/format";
import { PROVIDER_TYPE_OPTIONS, operationLabel, providerType, resultLabel } from "../utils/labels";

const OPERATION_OPTIONS = [
  { value: "FOLLOWUP", label: "追问生成" },
  { value: "STORY", label: "故事生成" },
  { value: "AFFIRMATION", label: "肯定句生成" },
  { value: "AUDIO_SUBMIT", label: "音频提交" },
  { value: "AUDIO_QUERY", label: "音频查询" },
];

const data = ref<PageData<AdminAiLog>>({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
const requestId = ref("");
const providerTypeFilter = ref("");
const operation = ref("");
const pageSize = ref(20);
const loading = ref(false);
const autoRefresh = ref(false);
const currentPage = ref(1);
let timer: number | null = null;

async function load(page = 0) {
  loading.value = true;
  currentPage.value = page + 1;
  try {
    const params: Record<string, unknown> = { page, size: pageSize.value };
    if (requestId.value.trim()) {
      params.requestId = requestId.value.trim();
    }
    if (providerTypeFilter.value) {
      params.providerType = providerTypeFilter.value;
    }
    if (operation.value) {
      params.operation = operation.value;
    }
    data.value = await adminApi.aiLogs(params);
  } catch (e) {
    ElMessage.error("加载 AI 日志失败");
  } finally {
    loading.value = false;
  }
}

function reset() {
  requestId.value = "";
  providerTypeFilter.value = "";
  operation.value = "";
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

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success("已复制 requestId");
  } catch (e) {
    ElMessage.error("复制失败");
  }
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
    <PageHeader title="AI 调用日志" subtitle="按 requestId 串联一次生成的完整链路（文本 / 音频 / 审核）">
      <template #actions>
        <el-switch v-model="autoRefresh" active-text="自动刷新(5s)" @change="toggleAuto" />
        <el-button :icon="Refresh" :loading="loading" @click="load(data.number)">刷新</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="card">
      <div class="toolbar">
        <el-input
          v-model="requestId"
          placeholder="按 requestId 查询"
          :prefix-icon="Search"
          clearable
          style="width: 240px"
          @keyup.enter="load(0)"
          @clear="load(0)"
        />
        <el-select v-model="providerTypeFilter" placeholder="全部类型" clearable style="width: 130px" @change="load(0)">
          <el-option v-for="opt in PROVIDER_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-select v-model="operation" placeholder="全部操作" clearable style="width: 150px" @change="load(0)">
          <el-option v-for="opt in OPERATION_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-button type="primary" @click="load(0)">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <span class="total">共 {{ data.totalElements }} 条</span>
      </div>

      <el-table :data="data.content" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="时间" width="165">
          <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <StatusTag :label="providerType(row.providerType).label" :type="providerType(row.providerType).type" />
          </template>
        </el-table-column>
        <el-table-column prop="provider" label="供应商" width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">{{ operationLabel(row.operation).label }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">{{ fmtDuration(row.latencyMs) }}</template>
        </el-table-column>
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <StatusTag :label="resultLabel(row.success).label" :type="resultLabel(row.success).type" />
          </template>
        </el-table-column>
        <el-table-column label="Tokens（入/出）" width="130">
          <template #default="{ row }">
            <span v-if="row.inputTokens !== null || row.outputTokens !== null">
              {{ row.inputTokens ?? "—" }} / {{ row.outputTokens ?? "—" }}
            </span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="错误" min-width="180">
          <template #default="{ row }">
            <el-tooltip v-if="row.errorCode" :content="`${row.errorCode}：${row.errorMessage || ''}`" placement="top">
              <span class="error-code">{{ row.errorCode }}</span>
            </el-tooltip>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="requestId" min-width="230">
          <template #default="{ row }">
            <div class="request-id">
              <span class="mono">{{ row.requestId }}</span>
              <el-button text size="small" :icon="DocumentCopy" @click="copy(row.requestId)" />
            </div>
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
  flex-wrap: wrap;
}
.total {
  margin-left: auto;
  font-size: 12px;
  color: #909399;
}
.error-code {
  color: #ef4444;
  font-size: 12px;
  cursor: help;
}
.muted {
  color: #c0c4cc;
}
.request-id {
  display: flex;
  align-items: center;
  gap: 4px;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  color: #6b7280;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
