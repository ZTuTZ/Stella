<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { adminApi } from "../services/adminApi";
import type { AdminUserBrief, PageData } from "../services/adminApi";

// 用户列表（§20.2）：关键字搜索 + 封禁/解封
const router = useRouter();
const data = ref<PageData<AdminUserBrief>>({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
const keyword = ref("");
const loading = ref(false);

async function load(page = 0) {
  loading.value = true;
  try {
    data.value = await adminApi.users(keyword.value, page, 20);
  } finally {
    loading.value = false;
  }
}

async function toggleStatus(row: AdminUserBrief) {
  const next = row.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  await adminApi.setUserStatus(row.id, next);
  row.status = next;
  ElMessage.success(next === "DISABLED" ? "已封禁（token 立即失效）" : "已解封");
}

onMounted(() => load());
</script>

<template>
  <div>
    <el-input
      v-model="keyword"
      placeholder="昵称 / openid 前缀"
      style="width: 260px; margin-right: 12px"
      @keyup.enter="load(0)"
    />
    <el-button @click="load(0)">搜索</el-button>

    <el-table :data="data.content" v-loading="loading" style="margin-top: 16px">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="displayName" label="昵称" />
      <el-table-column prop="openidMasked" label="openid（脱敏）" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="onboardingCompleted" label="Onboarding" width="100">
        <template #default="{ row }">{{ row.onboardingCompleted ? "完成" : "未完成" }}</template>
      </el-table-column>
      <el-table-column prop="lastActiveAt" label="最近活跃" width="180" />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button size="small" @click="router.push(`/users/${row.id}`)">详情</el-button>
          <el-button size="small" :type="row.status === 'ACTIVE' ? 'danger' : 'success'" @click="toggleStatus(row)">
            {{ row.status === "ACTIVE" ? "封禁" : "解封" }}
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
