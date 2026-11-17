<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { Refresh, Search } from "@element-plus/icons-vue";
import { adminApi } from "../services/adminApi";
import type { AdminUserBrief, PageData } from "../services/adminApi";
import PageHeader from "../components/PageHeader.vue";
import StatusTag from "../components/StatusTag.vue";
import { fmtDateTime } from "../utils/format";
import { onboardingLabel, roleLabel, userStatus } from "../utils/labels";

const router = useRouter();
const data = ref<PageData<AdminUserBrief>>({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
const keyword = ref("");
const pageSize = ref(20);
const loading = ref(false);
const currentPage = ref(1);

async function load(page = 0) {
  loading.value = true;
  currentPage.value = page + 1;
  try {
    data.value = await adminApi.users(keyword.value.trim(), page, pageSize.value);
  } catch (e) {
    ElMessage.error("加载用户列表失败");
  } finally {
    loading.value = false;
  }
}

function reset() {
  keyword.value = "";
  load(0);
}

async function toggleStatus(row: AdminUserBrief) {
  const banning = row.status === "ACTIVE";
  if (banning) {
    await ElMessageBox.confirm(
      `确定封禁「${row.displayName}」？封禁后该用户所有登录态立即失效。`,
      "封禁用户",
      { confirmButtonText: "封禁", cancelButtonText: "取消", type: "warning" },
    );
  }
  const next = banning ? "DISABLED" : "ACTIVE";
  await adminApi.setUserStatus(row.id, next);
  row.status = next;
  ElMessage.success(banning ? "已封禁" : "已解封");
}

onMounted(() => load());
</script>

<template>
  <div>
    <PageHeader title="用户管理" subtitle="查询、查看与封禁小程序用户">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load(data.number)">刷新</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索昵称 / openid 前缀"
          :prefix-icon="Search"
          clearable
          style="width: 280px"
          @keyup.enter="load(0)"
          @clear="load(0)"
        />
        <el-button type="primary" @click="load(0)">搜索</el-button>
        <el-button @click="reset">重置</el-button>
        <span class="total">共 {{ data.totalElements }} 位用户</span>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="data.content" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="displayName" label="昵称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="openidMasked" label="openid（脱敏）" min-width="160" />
        <el-table-column label="角色" width="90">
          <template #default="{ row }">
            <StatusTag :label="roleLabel(row.role).label" :type="roleLabel(row.role).type" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :label="userStatus(row.status).label" :type="userStatus(row.status).type" />
          </template>
        </el-table-column>
        <el-table-column label="引导" width="90">
          <template #default="{ row }">
            <StatusTag :label="onboardingLabel(row.onboardingCompleted).label" :type="onboardingLabel(row.onboardingCompleted).type" />
          </template>
        </el-table-column>
        <el-table-column label="最近活跃" width="170">
          <template #default="{ row }">{{ fmtDateTime(row.lastActiveAt) }}</template>
        </el-table-column>
        <el-table-column label="注册时间" width="170">
          <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="router.push(`/users/${row.id}`)">详情</el-button>
            <el-button
              size="small"
              :type="row.status === 'ACTIVE' ? 'danger' : 'success'"
              plain
              @click="toggleStatus(row)"
            >
              {{ row.status === "ACTIVE" ? "封禁" : "解封" }}
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
.toolbar-card,
.table-card {
  border-radius: 12px;
  border: 1px solid #ebeef5;
  margin-bottom: 16px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}
.total {
  margin-left: auto;
  font-size: 12px;
  color: #909399;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
