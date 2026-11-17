<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  Aim,
  DataBoard,
  Document,
  List,
  MagicStick,
  Setting,
  SwitchButton,
  Tickets,
  User,
} from "@element-plus/icons-vue";
import { ElMessageBox } from "element-plus";
import { useAdminStore } from "./services/http";

const route = useRoute();
const router = useRouter();
const store = useAdminStore();

const menu = [
  { path: "/dashboard", label: "总览", icon: DataBoard },
  { path: "/users", label: "用户", icon: User },
  { path: "/goals", label: "目标", icon: Aim },
  { path: "/stories", label: "故事", icon: Document },
  { path: "/tasks", label: "任务", icon: List },
  { path: "/prompts", label: "Prompt 管理", icon: MagicStick },
  { path: "/ai-logs", label: "AI 日志", icon: Tickets },
  { path: "/settings", label: "设置", icon: Setting },
];

const currentTitle = computed(() => (route.meta.title as string) || "管理后台");

async function onLogout() {
  await ElMessageBox.confirm("确定退出登录？", "退出", {
    confirmButtonText: "退出",
    cancelButtonText: "取消",
    type: "warning",
  });
  store.clearSession();
  router.push("/login");
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="232px" class="aside">
      <div class="brand">
        <div class="logo">FS</div>
        <div class="brand-text">
          <div class="brand-name">未来自我</div>
          <div class="brand-sub">Future Self 管理后台</div>
        </div>
      </div>
      <el-menu :default-active="route.path" router class="menu">
        <el-menu-item v-for="item in menu" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <span class="page-title">{{ currentTitle }}</span>
          <el-tag size="small" type="info" effect="plain" class="env-tag">内测环境</el-tag>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click">
            <span class="user-trigger">
              <el-avatar :size="26" class="avatar">{{ (store.username || "A").slice(0, 1).toUpperCase() }}</el-avatar>
              <span class="username">{{ store.username || "管理员" }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :icon="SwitchButton" @click="onLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style>
body {
  margin: 0;
  background: #f5f6fa;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", Arial, sans-serif;
  color: #1f2937;
}
* {
  box-sizing: border-box;
}
</style>

<style scoped>
.layout {
  min-height: 100vh;
}
.aside {
  background: #fff;
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px;
  border-bottom: 1px solid #f2f3f5;
}
.logo {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #6366f1, #a855f7);
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
}
.brand-name {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.3;
}
.brand-sub {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}
.menu {
  border-right: none;
  padding: 8px;
  flex: 1;
}
.menu :deep(.el-menu-item) {
  height: 42px;
  line-height: 42px;
  border-radius: 8px;
  margin-bottom: 4px;
  color: #4b5563;
}
.menu :deep(.el-menu-item.is-active) {
  background: #eef2ff;
  color: #4f46e5;
  font-weight: 600;
}
.header {
  height: 60px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.page-title {
  font-size: 15px;
  font-weight: 600;
}
.env-tag {
  border-radius: 999px;
}
.header-right {
  display: flex;
  align-items: center;
}
.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
}
.user-trigger:hover {
  background: #f5f7fa;
}
.avatar {
  background: #4f46e5;
  font-size: 12px;
}
.username {
  font-size: 13px;
  color: #4b5563;
}
.main {
  padding: 20px;
  max-width: 1360px;
  width: 100%;
  margin: 0 auto;
}
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(6px);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
