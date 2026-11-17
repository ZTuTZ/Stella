/**
 * Admin API 封装（§12.8）。统一解包 {success,code,message,data} envelope。
 */
import { http } from "./http";

interface Envelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

async function unwrap<T>(p: Promise<{ data: Envelope<T> }>): Promise<T> {
  const res = await p;
  if (!res.data.success) {
    throw new Error(res.data.message || res.data.code);
  }
  return res.data.data;
}

export interface PageData<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AdminUserBrief {
  id: number;
  displayName: string;
  openidMasked: string;
  role: string;
  status: string;
  onboardingCompleted: boolean;
  lastActiveAt: string | null;
  createdAt: string;
}

export interface AdminPromptSummary {
  id: number;
  promptKey: string;
  version: number;
  enabled: boolean;
  notes: string | null;
  updatedAt: string | null;
}

export interface AdminTask {
  id: number;
  userId: number;
  goalId: number;
  storyId: number | null;
  status: string;
  progress: number;
  retryCount: number;
  errorStage: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  createdAt: string;
  finishedAt: string | null;
}

export interface AdminAiLog {
  id: number;
  requestId: string;
  userId: number | null;
  generationTaskId: number | null;
  providerType: string;
  provider: string;
  model: string | null;
  operation: string;
  latencyMs: number;
  success: boolean;
  inputTokens: number | null;
  outputTokens: number | null;
  errorCode: string | null;
  errorMessage: string | null;
  createdAt: string;
}

export const adminApi = {
  login: (username: string, password: string) =>
    unwrap<{ accessToken: string; expiresIn: number; admin: { username: string; role: string } }>(
      http.post("/admin/auth/login", { username, password }),
    ),
  users: (keyword: string, page: number, size: number) =>
    unwrap<PageData<AdminUserBrief>>(http.get("/admin/users", { params: { keyword: keyword || undefined, page, size } })),
  userDetail: (id: number) => unwrap<Record<string, unknown>>(http.get(`/admin/users/${id}`)),
  setUserStatus: (id: number, status: string) =>
    unwrap<{ id: number; status: string }>(http.put(`/admin/users/${id}/status`, { status })),
  goals: (params: Record<string, unknown>) =>
    unwrap<PageData<Record<string, unknown>>>(http.get("/admin/goals", { params })),
  stories: (params: Record<string, unknown>) =>
    unwrap<PageData<Record<string, unknown>>>(http.get("/admin/stories", { params })),
  tasks: (params: Record<string, unknown>) =>
    unwrap<PageData<AdminTask>>(http.get("/admin/tasks", { params })),
  retryTask: (id: number) =>
    unwrap<{ id: number; status: string; retryCount: number }>(http.post(`/admin/tasks/${id}/retry`)),
  prompts: () => unwrap<AdminPromptSummary[]>(http.get("/admin/prompts")),
  promptDetail: (id: number) =>
    unwrap<AdminPromptSummary & { systemPrompt: string; userTemplate: string; outputSchemaJson: string | null }>(
      http.get(`/admin/prompts/${id}`),
    ),
  createPrompt: (payload: { promptKey: string; systemPrompt: string; userTemplate: string; outputSchemaJson?: string; notes?: string }) =>
    unwrap<AdminPromptSummary>(http.post("/admin/prompts", payload)),
  enablePrompt: (id: number) => unwrap<AdminPromptSummary>(http.put(`/admin/prompts/${id}/enable`)),
  disablePrompt: (id: number) => unwrap<AdminPromptSummary>(http.put(`/admin/prompts/${id}/disable`)),
  aiLogs: (params: Record<string, unknown>) =>
    unwrap<PageData<AdminAiLog>>(http.get("/admin/ai-logs", { params })),
  dashboardSummary: () =>
    unwrap<Record<string, number>>(http.get("/admin/dashboard/summary")),
};
