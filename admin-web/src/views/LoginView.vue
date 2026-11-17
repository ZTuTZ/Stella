<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { adminApi } from "../services/adminApi";
import { useAdminStore } from "../services/http";

const router = useRouter();
const store = useAdminStore();
const username = ref("admin");
const password = ref("");
const loading = ref(false);

async function onLogin() {
  if (!username.value || !password.value) {
    ElMessage.warning("请输入用户名和密码");
    return;
  }
  loading.value = true;
  try {
    const data = await adminApi.login(username.value, password.value);
    store.setSession(data.accessToken, data.admin.username);
    router.push("/dashboard");
  } catch (err) {
    const status = (err as { response?: { status?: number } }).response?.status;
    ElMessage.error(status === 401 ? "用户名或密码错误" : "登录失败，请稍后重试");
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <template #header>Future Self 管理后台</template>
      <el-form label-width="72px" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="username" placeholder="admin" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password @keyup.enter="onLogin" />
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width: 100%" @click="onLogin">
          登录
        </el-button>
      </el-form>
      <div class="hint">凭据来自后端 ADMIN_BOOTSTRAP_USERNAME / ADMIN_BOOTSTRAP_PASSWORD</div>
    </el-card>
  </div>
</template>

<style scoped>
.login-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}
.login-card { width: 400px; }
.hint { margin-top: 12px; font-size: 12px; color: #999; }
</style>
