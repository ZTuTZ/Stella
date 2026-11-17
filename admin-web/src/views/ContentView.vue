<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { Refresh, Search } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { adminApi } from "../services/adminApi";
import type { PageData } from "../services/adminApi";
import PageHeader from "../components/PageHeader.vue";
import StatusTag from "../components/StatusTag.vue";
import { fmtDateTime } from "../utils/format";
import {
  GOAL_STATUS_OPTIONS,
  STORY_STATUS_OPTIONS,
  goalStatus,
  moderationStatus,
  storyStatus,
} from "../utils/labels";

/** 目标 / 故事通用列表：按 kind 切换列与筛选项 */
const props = defineProps<{ kind: "goals" | "stories" }>();

const data = ref<PageData<Record<string, unknown>>>({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
const userId = ref("");
const status = ref("");
const pageSize = ref(20);
const loading = ref(false);
const currentPage = ref(1);

const isGoals = computed(() => props.kind === "goals");
const title = computed(() => (isGoals.value ? "目标管理" : "故事管理"));
const subtitle = computed(() =>
  isGoals.value ? "查看用户目标、状态与创建时间" : "查看故事、审核状态与生成来源（provider / model）",
);
const statusOptions = computed(() => (isGoals.value ? GOAL_STATUS_OPTIONS : STORY_STATUS_OPTIONS));

async function load(page = 0) {
  loading.value = true;
  currentPage.value = page + 1;
  try {
    const params: Record<string, unknown> = { page, size: pageSize.value };
    if (userId.value.trim()) {
      params.userId = Number(userId.value.trim());
    }
    if (status.value) {
      params.status = status.value;
    }
    data.value = isGoals.value ? await adminApi.goals(params) : await adminApi.stories(params);
  } catch (e) {
    ElMessage.error("加载列表失败");
  } finally {
    loading.value = false;
  }
}

function reset() {
  userId.value = "";
  status.value = "";
  load(0);
}

onMounted(() => load());
</script>

<template>
  <div>
    <PageHeader :title="title" :subtitle="subtitle">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load(data.number)">刷新</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="card">
      <div class="toolbar">
        <el-input
          v-model="userId"
          placeholder="按用户 ID 过滤"
          :prefix-icon="Search"
          clearable
          style="width: 200px"
          @keyup.enter="load(0)"
          @clear="load(0)"
        />
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 150px" @change="load(0)">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-button type="primary" @click="load(0)">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <span class="total">共 {{ data.totalElements }} 条</span>
      </div>

      <el-table :data="data.content" v-loading="loading" stripe class="table">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="userId" label="用户" width="90" />
        <template v-if="isGoals">
          <el-table-column prop="category" label="分类" width="120" />
          <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
          <el-table-column prop="targetTimeText" label="期望时间" width="140" />
        </template>
        <template v-else>
          <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
          <el-table-column prop="moderationStatus" label="审核" width="110">
            <template #default="{ row }">
              <StatusTag
                :label="moderationStatus(row.moderationStatus as string).label"
                :type="moderationStatus(row.moderationStatus as string).type"
              />
            </template>
          </el-table-column>
          <el-table-column prop="provider" label="供应商" width="100" />
          <el-table-column prop="model" label="模型" width="130" show-overflow-tooltip />
        </template>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <StatusTag
              v-if="isGoals"
              :label="goalStatus(row.status as string).label"
              :type="goalStatus(row.status as string).type"
            />
            <StatusTag
              v-else
              :label="storyStatus(row.status as string).label"
              :type="storyStatus(row.status as string).type"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ fmtDateTime(row.createdAt as string) }}</template>
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
.table {
  width: 100%;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
