/**
 * API 封装（实施文档 §12）。路径与后端 /api/v1 一一对应。
 */
import { api, RequestOptions } from "./request";
import {
  FollowupQuestion,
  GoalResponse,
  HomeResponse,
  LifeProfileResponse,
  LoginUser,
  MeResponse,
  StoryPageResponse,
  StoryResponse,
  TaskResponse,
} from "../types/api";

export const authApi = {
  /** 静默登录（走 stores/session，处理 mock 稳定身份） */
  login: (force = false) => import("../stores/session").then((m) => m.ensureLogin(force)),
};

export const meApi = {
  me: (options?: RequestOptions) => api.get<MeResponse>("/api/v1/me", options),
  updateDisplayName: (displayName: string) => api.patch<MeResponse>("/api/v1/me", { displayName }),
  completeOnboarding: (options?: RequestOptions) => api.post<MeResponse>("/api/v1/me/onboarding/complete", undefined, options),
};

export interface LifeProfileUpdate {
  currentSituation?: string;
  focusAreas?: string[];
  importantPeople?: { name: string; relation: string; note?: string }[];
  preferences?: Record<string, string>;
}

export const profileApi = {
  get: () => api.get<LifeProfileResponse>("/api/v1/me/life-profile"),
  update: (payload: LifeProfileUpdate) => api.put<LifeProfileResponse>("/api/v1/me/life-profile", payload),
};

export const goalApi = {
  list: (options?: RequestOptions) => api.get<GoalResponse[]>("/api/v1/goals", options),
  create: (payload: { goalCategory: string; goalTitle: string; goalDescription: string; targetTimeText?: string }) =>
    api.post<GoalResponse>("/api/v1/goals", payload),
  followupQuestions: (goalId: number) =>
    api.post<{ questions: FollowupQuestion[] }>(`/api/v1/goals/${goalId}/followup-questions`),
  followupAnswers: (goalId: number, answers: { questionId: string; answer: string }[]) =>
    api.put<GoalResponse>(`/api/v1/goals/${goalId}/followup-answers`, { answers }),
};

export const generationApi = {
  generateStory: (goalId: number) =>
    api.post<{ taskId: number; status: string }>(`/api/v1/goals/${goalId}/generate-story`),
  task: (taskId: number) => api.get<TaskResponse>(`/api/v1/generation-tasks/${taskId}`),
};

export const storyApi = {
  detail: (storyId: number) => api.get<StoryResponse>(`/api/v1/stories/${storyId}`),
  list: (page: number, size: number, favoriteOnly: boolean, options?: RequestOptions) =>
    api.get<StoryPageResponse>(
      `/api/v1/stories?page=${page}&size=${size}&favorite=${favoriteOnly}`,
      options,
    ),
  setFavorite: (storyId: number, favorite: boolean) =>
    api.put<{ id: number; favorite: boolean }>(`/api/v1/stories/${storyId}/favorite`, { favorite }),
  retryAudio: (storyId: number) =>
    api.post<{ taskId: number; taskStatus: string; audioStatus: string | null }>(
      `/api/v1/stories/${storyId}/retry-audio`),
};

export const homeApi = {
  home: (options?: RequestOptions) => api.get<HomeResponse>("/api/v1/home", options),
};

export type { LoginUser };
