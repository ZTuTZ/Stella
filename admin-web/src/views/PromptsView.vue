<script setup lang="ts">
import { onMounted, ref } from "vue";
import { DocumentCopy, Plus, Refresh } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { adminApi } from "../services/adminApi";
import type { AdminPromptSummary } from "../services/adminApi";
import PageHeader from "../components/PageHeader.vue";
import StatusTag from "../components/StatusTag.vue";
import { fmtDateTime } from "../utils/format";

interface PromptDetail extends AdminPromptSummary {
  systemPrompt: string;
  userTemplate: string;
  outputSchemaJson: string | null;
}

const PROMPT_KEYS = [
  { value: "FOLLOWUP", label: "FOLLOWUP · AI 追问" },
  { value: "STORY", label: "STORY · 未来故事" },
  { value: "AFFIRMATION", label: "AFFIRMATION · 每日肯定句" },
];

const prompts = ref<AdminPromptSummary[]>([]);
const loading = ref(true);
const detail = ref<PromptDetail | null>(null);
const detailVisible = ref(false);
const createVisible = ref(false);
const creating = ref(false);
const form = ref({ promptKey: "STORY", systemPrompt: "", userTemplate: "", notes: "" });

function keyLabel(key: string): string {
  const found = PROMPT_KEYS.find((k) => k.value === key);
  return found ? found.label.split(" · ")[1] : key;
}

async function load() {
  loading.value = true;
  try {
    prompts.value = await adminApi.prompts();
  } catch (e) {
    ElMessage.error("加载 Prompt 失败");
  } finally {
    loading.value = false;
  }
}

async function openDetail(row: AdminPromptSummary) {
  try {
    detail.value = (await adminApi.promptDetail(row.id)) as PromptDetail;
    detailVisible.value = true;
  } catch (e) {
    ElMessage.error("加载 Prompt 详情失败");
  }
}

async function onEnable(row: AdminPromptSummary) {
  await ElMessageBox.confirm(
    `启用「${keyLabel(row.promptKey)} v${row.version}」？同 Key 的其他版本会被自动禁用，新请求立即生效。`,
    "切换 Prompt 版本",
    { confirmButtonText: "启用", cancelButtonText: "取消" },
  );
  await adminApi.enablePrompt(row.id);
  ElMessage.success("已启用");
  load();
}

async function onDisable(row: AdminPromptSummary) {
  await ElMessageBox.confirm(`禁用「${keyLabel(row.promptKey)} v${row.version}」？`, "禁用 Prompt", {
    confirmButtonText: "禁用",
    cancelButtonText: "取消",
    type: "warning",
  });
  await adminApi.disablePrompt(row.id);
  ElMessage.success("已禁用");
  load();
}

async function onCreate() {
  if (!form.value.systemPrompt.trim() || !form.value.userTemplate.trim()) {
    ElMessage.warning("系统提示词与用户模板不能为空");
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
    ElMessage.success("新版本已创建（默认禁用，请在列表中启用）");
    createVisible.value = false;
    form.value = { promptKey: "STORY", systemPrompt: "", userTemplate: "", notes: "" };
    load();
  } catch (e) {
    ElMessage.error("创建失败");
  } finally {
    creating.value = false;
  }
}

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success("已复制");
  } catch (e) {
    ElMessage.error("复制失败，请手动选择复制");
  }
}

onMounted(load);
</script>

<template>
  <div>
    <PageHeader title="Prompt 管理" subtitle="版本化 Prompt：新建版本不覆盖历史，启用后立即生效">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="createVisible = true">新建版本</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="card">
      <el-table :data="prompts" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="类型" width="180">
          <template #default="{ row }">
            <div class="prompt-key">
              <span class="key-label">{{ keyLabel(row.promptKey) }}</span>
              <span class="key-code">{{ row.promptKey }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="90">
          <template #default="{ row }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <StatusTag :label="row.enabled ? '启用中' : '已禁用'" :type="row.enabled ? 'success' : 'info'" />
          </template>
        </el-table-column>
        <el-table-column prop="notes" label="备注" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.notes || "—" }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ fmtDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button size="small" type="success" plain :disabled="row.enabled" @click="onEnable(row)">启用</el-button>
            <el-button size="small" :disabled="!row.enabled" @click="onDisable(row)">禁用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建版本 -->
    <el-drawer v-model="createVisible" title="新建 Prompt 版本" size="560px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="Prompt 类型" required>
          <el-select v-model="form.promptKey" style="width: 100%">
            <el-option v-for="k in PROMPT_KEYS" :key="k.value" :label="k.label" :value="k.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="系统提示词（systemPrompt）" required>
          <el-input v-model="form.systemPrompt" type="textarea" :rows="10" placeholder="模型角色、写作红线、输出要求…" />
        </el-form-item>
        <el-form-item label="用户模板（userTemplate，支持 {{占位符}}）" required>
          <el-input v-model="form.userTemplate" type="textarea" :rows="12" placeholder="用户资料与目标信息注入模板…" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.notes" placeholder="例如：v2 调整语气更克制" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="onCreate">创建（默认禁用）</el-button>
      </template>
    </el-drawer>

    <!-- 详情 -->
    <el-drawer v-model="detailVisible" :title="detail ? `${keyLabel(detail.promptKey)} v${detail.version}` : ''" size="640px">
      <template v-if="detail">
        <div class="detail-head">
          <StatusTag :label="detail.enabled ? '启用中' : '已禁用'" :type="detail.enabled ? 'success' : 'info'" />
          <span class="muted">更新于 {{ fmtDateTime(detail.updatedAt) }}</span>
        </div>
        <el-divider content-position="left">系统提示词</el-divider>
        <div class="block">
          <pre class="code">{{ detail.systemPrompt }}</pre>
          <el-button text size="small" :icon="DocumentCopy" @click="copy(detail.systemPrompt)">复制</el-button>
        </div>
        <el-divider content-position="left">用户模板</el-divider>
        <div class="block">
          <pre class="code">{{ detail.userTemplate }}</pre>
          <el-button text size="small" :icon="DocumentCopy" @click="copy(detail.userTemplate)">复制</el-button>
        </div>
        <el-divider content-position="left">输出 Schema</el-divider>
        <pre class="code">{{ detail.outputSchemaJson || "—" }}</pre>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.card {
  border-radius: 12px;
  border: 1px solid #ebeef5;
}
.prompt-key {
  display: flex;
  flex-direction: column;
}
.key-label {
  font-size: 13px;
  color: #1f2937;
}
.key-code {
  font-size: 11px;
  color: #a8abb2;
}
.detail-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.muted {
  color: #909399;
  font-size: 12px;
}
.block {
  position: relative;
}
.code {
  margin: 0;
  padding: 14px 16px;
  background: #f7f8fa;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #4b5563;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 320px;
  overflow: auto;
}
</style>
