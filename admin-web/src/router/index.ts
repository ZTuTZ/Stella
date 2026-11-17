import { createRouter, createWebHistory } from "vue-router";
import { useAdminStore } from "../services/http";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", redirect: "/dashboard" },
    { path: "/login", component: () => import("../views/LoginView.vue") },
    { path: "/dashboard", component: () => import("../views/DashboardView.vue"), meta: { admin: true } },
    { path: "/users", component: () => import("../views/UsersView.vue"), meta: { admin: true } },
    { path: "/users/:id", component: () => import("../views/UserDetailView.vue"), meta: { admin: true } },
    { path: "/goals", component: () => import("../views/GoalsView.vue"), meta: { admin: true } },
    { path: "/stories", component: () => import("../views/StoriesView.vue"), meta: { admin: true } },
    { path: "/tasks", component: () => import("../views/TasksView.vue"), meta: { admin: true } },
    { path: "/prompts", component: () => import("../views/PromptsView.vue"), meta: { admin: true } },
    { path: "/ai-logs", component: () => import("../views/AiLogsView.vue"), meta: { admin: true } },
    { path: "/settings", component: () => import("../views/SettingsView.vue"), meta: { admin: true } },
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
