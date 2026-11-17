<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowLeft, Refresh } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { adminApi } from "../services/adminApi";
import PageHeader from "../components/PageHeader.vue";
import StatusTag from "../components/StatusTag.vue";
import { fmtDateTime } from "../utils/format";
import { goalStatus, moderationStatus, roleLabel, storyStatus, taskStatus, userStatus, onboardingLabel } from "../utils/labels";

const route = useRoute();
const router = useRouter();
const userId = Number(route.params.id);
const detail = ref<Record<string, any> | null>(null);
const loading = ref(true);
const activeTab = ref("profile");

const user = computed(() => (detail.value ? detail.value.user : null));

async function load() {
  loading.value = true;
  try {
    detail.value = await adminApi.userDetail(userId);
  } catch (e) {
    ElMessage.error("加载用户详情失败");
  } finally {
    loading.value = false;
  }
}

function pretty(json: unknown): string {
  if (json === null || json === undefined) {
    return "—";
  }
  try {
    return JSON.stringify(json, null, 2);
  } catch (e) {
    return String(json);
  }
}

onMounted(load);
</script>

<template>
  <div>
    <PageHeader :title="user ? `用户详情 · ${user.displayName}` : '用户详情'" subtitle="基础信息、人生档案与生成记录（仅供内测排查使用）">
      <template #actions>
        <el-button :icon="ArrowLeft" @click="router.push('/users')">返回列表</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <el-skeleton v-if="loading" :rows="8" animated />

    <template v-else-if="detail">
      <el-card shadow="never" class="card">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="用户 ID">{{ user.id }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ user.displayName }}</el-descriptions-item>
          <el-descriptions-item label="openid（脱敏）">{{ user.openidMasked }}</el-descriptions-item>
          <el-descriptions-item label="角色">
            <StatusTag :label="roleLabel(user.role).label" :type="roleLabel(user.role).type" />
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag :label="userStatus(user.status).label" :type="userStatus(user.status).type" />
          </el-descriptions-item>
          <el-descriptions-item label="引导完成">
            <StatusTag :label="onboardingLabel(user.onboardingCompleted).label" :type="onboardingLabel(user.onboardingCompleted).type" />
          </el-descriptions-item>
          <el-descriptions-item label="最近活跃">{{ fmtDateTime(user.lastActiveAt) }}</el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ fmtDateTime(user.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="档案版本">{{ detail.lifeProfile?.profileVersion ?? "—" }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" class="card">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="人生档案" name="profile">
            <el-alert
              title="包含用户敏感信息，仅用于内测问题排查，请勿外传"
              type="warning"
              :closable="false"
              show-icon
              class="alert"
            />
            <pre class="json">{{ pretty(detail.lifeProfile) }}</pre>
          </el-tab-pane>

          <el-tab-pane :label="`目标（${(detail.goals || []).length}）`" name="goals">
            <el-table :data="detail.goals" size="small" stripe>
              <el-table-column prop="id" label="ID" width="70" />
              <el-table-column prop="category" label="分类" width="110" />
              <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <StatusTag :label="goalStatus(row.status).label" :type="goalStatus(row.status).type" />
                </template>
              </el-table-column>
              <el-table-column label="创建时间" width="170">
                <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="`故事（${(detail.stories || []).length}）`" name="stories">
            <el-table :data="detail.stories" size="small" stripe>
              <el-table-column prop="id" label="ID" width="70" />
              <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <StatusTag :label="storyStatus(row.status).label" :type="storyStatus(row.status).type" />
                </template>
              </el-table-column>
              <el-table-column prop="moderationStatus" label="审核" width="110">
                <template #default="{ row }">
                  <StatusTag :label="moderationStatus(row.moderationStatus).label" :type="moderationStatus(row.moderationStatus).type" />
                </template>
              </el-table-column>
              <el-table-column prop="provider" label="供应商" width="100" />
              <el-table-column prop="model" label="模型" width="130" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="`最近任务（${(detail.recentTasks || []).length}）`" name="tasks">
            <el-table :data="detail.recentTasks" size="small" stripe>
              <el-table-column prop="id" label="ID" width="70" />
              <el-table-column prop="status" label="状态" width="140">
                <template #default="{ row }">
                  <StatusTag :label="taskStatus(row.status).label" :type="taskStatus(row.status).type" />
                </template>
              </el-table-column>
              <el-table-column prop="retryCount" label="重试次数" width="90" />
              <el-table-column prop="errorStage" label="错误阶段" width="110" />
              <el-table-column prop="errorCode" label="错误码" width="190" show-overflow-tooltip />
              <el-table-column prop="errorMessage" label="错误信息" min-width="240" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.card {
  border-radius: 12px;
  border: 1px solid #ebeef5;
  margin-bottom: 16px;
}
.alert {
  margin-bottom: 12px;
}
.json {
  margin: 0;
  padding: 14px 16px;
  background: #f7f8fa;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #4b5563;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 420px;
  overflow: auto;
}
</style>
