import { createRouter, createWebHistory } from "vue-router";
import { useAdminStore } from "../services/http";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", redirect: "/dashboard" },
    { path: "/login", component: () => import("../views/LoginView.vue") },
    { path: "/dashboard", component: () => import("../views/DashboardView.vue"), meta: { admin: true, title: "总览" } },
    { path: "/users", component: () => import("../views/UsersView.vue"), meta: { admin: true, title: "用户管理" } },
    { path: "/users/:id", component: () => import("../views/UserDetailView.vue"), meta: { admin: true, title: "用户详情" } },
    { path: "/goals", component: () => import("../views/GoalsView.vue"), meta: { admin: true, title: "目标管理" } },
    { path: "/stories", component: () => import("../views/StoriesView.vue"), meta: { admin: true, title: "故事管理" } },
    { path: "/tasks", component: () => import("../views/TasksView.vue"), meta: { admin: true, title: "生成任务" } },
    { path: "/prompts", component: () => import("../views/PromptsView.vue"), meta: { admin: true, title: "Prompt 管理" } },
    { path: "/ai-logs", component: () => import("../views/AiLogsView.vue"), meta: { admin: true, title: "AI 调用日志" } },
    { path: "/settings", component: () => import("../views/SettingsView.vue"), meta: { admin: true, title: "设置" } },
  ],
});

router.beforeEach((to) => {
  const store = useAdminStore();
  if (to.meta.admin && !store.token) {
    return "/login";
  }
  if (to.path === "/login" && store.token) {
    return "/dashboard";
  }
  return true;
});

export default router;
