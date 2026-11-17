<script setup lang="ts">
import { useRouter } from "vue-router";
import { useAdminStore } from "./services/http";

// 管理后台布局（§4.3）：侧边菜单 + 内容区。内测运维用，不做重度视觉定制。
const router = useRouter();
const store = useAdminStore();

const menu = [
  { path: "/dashboard", label: "Dashboard" },
  { path: "/users", label: "用户" },
  { path: "/goals", label: "目标" },
  { path: "/stories", label: "故事" },
  { path: "/tasks", label: "任务" },
  { path: "/prompts", label: "Prompt" },
  { path: "/ai-logs", label: "AI 日志" },
  { path: "/settings", label: "设置" },
];

function onLogout() {
  store.clearSession();
  router.push("/login");
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="200px" class="aside">
      <div class="brand">Future Self 后台</div>
      <el-menu :default-active="$route.path" router>
        <el-menu-item v-for="item in menu" :key="item.path" :index="item.path">
          {{ item.label }}
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span>内测运维控制台</span>
        <span class="user">
          {{ store.username }}
          <el-button size="small" text @click="onLogout">退出</el-button>
        </span>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style>
body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", Arial, sans-serif; }
.layout { min-height: 100vh; }
.aside { border-right: 1px solid #eee; }
.brand { padding: 20px 16px; font-weight: 600; }
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #eee;
}
.user { display: flex; align-items: center; gap: 8px; }
</style>
