import { createRouter, createWebHistory } from "vue-router";

// Phase 0 骨架路由；Phase 8 按 §4.3 补全：/users /goals /stories /tasks /prompts /ai-logs /settings
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", redirect: "/dashboard" },
    {
      path: "/login",
      component: () => import("../views/LoginView.vue"),
    },
    {
      path: "/dashboard",
      component: () => import("../views/DashboardView.vue"),
    },
  ],
});

export default router;
