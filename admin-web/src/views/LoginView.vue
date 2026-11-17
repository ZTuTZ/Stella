<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { Lock, User } from "@element-plus/icons-vue";
import { adminApi } from "../services/adminApi";
import { useAdminStore } from "../services/http";

const router = useRouter();
const store = useAdminStore();
const formRef = ref<FormInstance>();
const username = ref("admin");
const password = ref("");
const loading = ref(false);

const rules: FormRules = {
  username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }],
};

async function onLogin() {
  if (!formRef.value) {
    return;
  }
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) {
    return;
  }
  loading.value = true;
  try {
    const data = await adminApi.login(username.value.trim(), password.value);
    store.setSession(data.accessToken, data.admin.username);
    ElMessage.success("登录成功");
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
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <div class="logo">FS</div>
        <div>
          <div class="brand-name">未来自我</div>
          <div class="brand-sub">Future Self 管理后台</div>
        </div>
      </div>

      <el-form ref="formRef" :model="{ username, password }" :rules="rules" size="large" @submit.prevent="onLogin">
        <el-form-item prop="username">
          <el-input v-model="username" placeholder="用户名" :prefix-icon="User" autocomplete="username" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
            autocomplete="current-password"
            @keyup.enter="onLogin"
          />
        </el-form-item>
        <el-button type="primary" size="large" class="submit" :loading="loading" @click="onLogin">登 录</el-button>
      </el-form>

      <div class="hint">
        账号由后端 <code>ADMIN_BOOTSTRAP_USERNAME / ADMIN_BOOTSTRAP_PASSWORD</code> 初始化
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(1200px 600px at 20% 0%, #eef2ff 0%, #f5f6fa 45%, #faf5ff 100%);
}
.login-card {
  width: 400px;
  padding: 32px 32px 24px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 12px 40px rgba(31, 41, 55, 0.1);
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 28px;
}
.logo {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, #6366f1, #a855f7);
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.brand-name {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.3;
}
.brand-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.submit {
  width: 100%;
  margin-top: 4px;
}
.hint {
  margin-top: 18px;
  font-size: 12px;
  color: #a8abb2;
  line-height: 1.6;
}
.hint code {
  background: #f5f7fa;
  padding: 1px 4px;
  border-radius: 4px;
  color: #606266;
}
</style>
