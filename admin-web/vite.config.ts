import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

// 管理后台 V1：内测运维用，不做重度定制。
// /api 代理到本地后端（Phase 2 起生效）。
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
