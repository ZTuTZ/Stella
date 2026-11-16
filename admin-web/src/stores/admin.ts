import { defineStore } from "pinia";
import { ref } from "vue";

/**
 * 管理员会话 store（Phase 0 占位）。
 * Phase 8 接入 /api/v1/admin/auth/login 与 token 管理（token 只存内存/内存态，不落 localStorage 明文可再评估）。
 */
export const useAdminStore = defineStore("admin", () => {
  const token = ref<string>("");
  const username = ref<string>("");

  function setSession(nextToken: string, nextUsername: string) {
    token.value = nextToken;
    username.value = nextUsername;
  }

  function clearSession() {
    token.value = "";
    username.value = "";
  }

  return { token, username, setSession, clearSession };
});
