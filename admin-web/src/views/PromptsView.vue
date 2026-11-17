<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { adminApi } from "../services/adminApi";
import type { AdminPromptSummary } from "../services/adminApi";

// Prompt 管理（§20.3）：版本列表 + 详情 + 新建版本 + 启用/禁用（同 key 仅一个 enabled）
const prompts = ref<AdminPromptSummary[]>([]);
const loading = ref(true);
const detail = ref<(AdminPromptSummary & { systemPrompt: string; userTemplate: string; outputSchemaJson: string | null }) | null>(null);
const creating = ref(false);
const form = ref({ promptKey: "FOLLOWUP", systemPrompt: "", userTemplate: "", notes: "" });

async function load() {
  loading.value = true;
  try {
    prompts.value = await adminApi.prompts();
  } finally {
    loading.value = false;
  }
}

async function openDetail(row: AdminPromptSummary) {
  detail.value = await adminApi.promptDetail(row.id);
}

async function onEnable(row: AdminPromptSummary) {
  await ElMessageBox.confirm(
    `启用 ${row.promptKey} v${row.version}？同 key 其他版本将被禁用（§14.4）`,
    "切换 Prompt 版本",
  );
  await adminApi.enablePrompt(row.id);
  ElMessage.success("已启用，新请求立即生效");
  await load();
}

async function onDisable(row: AdminPromptSummary) {
  await adminApi.disablePrompt(row.id);
  ElMessage.success("已禁用");
  await load();
}

async function onCreate() {
  if (!form.value.systemPrompt || !form.value.userTemplate) {
    ElMessage.warning("systemPrompt / userTemplate 必填");
    return;
  }
  creating.value = true;
  try {
    await adminApi.createPrompt({
      promptKey: form.value.promptKey,
      systemPrompt: form.value.systemPrompt,
      userTemplate: form.value.userTemplate,
      notes: form.value.notes || undefined,
    });
    ElMessage.success("新版本已创建（默认禁用，请手动启用）");
    form.value.systemPrompt = "";
    form.value.userTemplate = "";
    form.value.notes = "";
    await load();
  } finally {
    creating.value = false;
  }
}

onMounted(() => load());
</script>

<template>
  <div v-loading="loading">
    <el-card>
      <template #header>新建版本（不覆盖历史，启用后生效）</template>
      <el-form label-width="110px">
        <el-form-item label="promptKey">
          <el-select v-model="form.promptKey" style="width: 220px">
            <el-option label="FOLLOWUP" value="FOLLOWUP" />
            <el-option label="STORY" value="STORY" />
            <el-option label="AFFIRMATION" value="AFFIRMATION" />
          </el-select>
        </el-form-item>
        <el-form-item label="systemPrompt">
          <el-input v-model="form.systemPrompt" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="userTemplate">
          <el-input v-model="form.userTemplate" type="textarea" :rows="6" />
        </el-form-item>
        <el-form-item label="notes">
          <el-input v-model="form.notes" />
        </el-form-item>
        <el-button type="primary" :loading="creating" @click="onCreate">创建版本</el-button>
      </el-form>
    </el-card>

    <el-table :data="prompts" style="margin-top: 16px">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="promptKey" label="Key" width="140" />
      <el-table-column prop="version" label="版本" width="80" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? "启用中" : "禁用" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="notes" label="备注" />
      <el-table-column prop="updatedAt" label="更新时间" width="180" />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button size="small" @click="openDetail(row)">详情</el-button>
          <el-button size="small" type="success" :disabled="row.enabled" @click="onEnable(row)">启用</el-button>
          <el-button size="small" :disabled="!row.enabled" @click="onDisable(row)">禁用</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="detail" :title="detail ? `${detail.promptKey} v${detail.version}` : ''" width="720px">
      <template v-if="detail">
        <h4>systemPrompt</h4>
        <pre class="prompt">{{ detail.systemPrompt }}</pre>
        <h4>userTemplate</h4>
        <pre class="prompt">{{ detail.userTemplate }}</pre>
        <h4>outputSchemaJson</h4>
        <pre class="prompt">{{ detail.outputSchemaJson || "—" }}</pre>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.prompt { white-space: pre-wrap; word-break: break-all; background: #f7f8fa; padding: 12px; border-radius: 6px; font-size: 12px; }
</style>
