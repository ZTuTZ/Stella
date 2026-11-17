<script setup lang="ts">
import { onMounted, ref } from "vue";
import { adminApi } from "../services/adminApi";
import type { PageData } from "../services/adminApi";

// 目标/故事列表：通用分页表格视图
const props = defineProps<{ kind: "goals" | "stories" }>();

const data = ref<PageData<Record<string, unknown>>>({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
const userId = ref<string>("");
const loading = ref(false);

async function load(page = 0) {
  loading.value = true;
  try {
    const params: Record<string, unknown> = { page, size: 20 };
    if (userId.value) {
      params.userId = Number(userId.value);
    }
    data.value = props.kind === "goals" ? await adminApi.goals(params) : await adminApi.stories(params);
  } finally {
    loading.value = false;
  }
}

onMounted(() => load());
</script>

<template>
  <div>
    <el-input
      v-model="userId"
      placeholder="按用户 ID 过滤"
      style="width: 200px; margin-right: 12px"
      @keyup.enter="load(0)"
    />
    <el-button @click="load(0)">过滤</el-button>

    <el-table :data="data.content" v-loading="loading" style="margin-top: 16px">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="userId" label="用户" width="90" />
      <template v-if="kind === 'goals'">
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="targetTimeText" label="期望时间" width="140" />
      </template>
      <template v-else>
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="moderationStatus" label="审核" width="100" />
        <el-table-column prop="provider" label="Provider" width="100" />
        <el-table-column prop="model" label="Model" width="120" />
      </template>
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column prop="createdAt" label="创建时间" width="180" />
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
