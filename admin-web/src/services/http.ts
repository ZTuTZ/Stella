import axios from "axios";
import { defineStore } from "pinia";
import { ref } from "vue";

/**
 * 管理员会话：token 存 localStorage（内测运维工具）；401 统一跳登录。
 * useAdminStore 必须在 pinia 安装后调用（拦截器内惰性调用）。
 */
export const useAdminStore = defineStore("admin", () => {
  const token = ref<string>(localStorage.getItem("fs.admin.token") || "");
  const username = ref<string>(localStorage.getItem("fs.admin.username") || "");

  function setSession(nextToken: string, nextUsername: string) {
    token.value = nextToken;
    username.value = nextUsername;
    localStorage.setItem("fs.admin.token", nextToken);
    localStorage.setItem("fs.admin.username", nextUsername);
  }

  function clearSession() {
    token.value = "";
    username.value = "";
    localStorage.removeItem("fs.admin.token");
    localStorage.removeItem("fs.admin.username");
  }

  return { token, username, setSession, clearSession };
});

const http = axios.create({ baseURL: "/api/v1", timeout: 15000 });

http.interceptors.request.use((config) => {
  const store = useAdminStore();
  if (store.token) {
    config.headers.Authorization = `Bearer ${store.token}`;
  }
  return config;
});

http.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      useAdminStore().clearSession();
      if (!location.pathname.startsWith("/login")) {
        location.href = "/login";
      }
    }
    return Promise.reject(err);
  },
);

export { http };
