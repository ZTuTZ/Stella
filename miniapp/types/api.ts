/**
 * API 类型定义（实施文档 §12 统一响应与 DTO）。
 */

/** 统一响应体（§12） */
export interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
  requestId: string;
}

/** 业务错误（request client 抛出） */
export interface ApiError {
  code: string;
  message: string;
  statusCode?: number;
}

export interface LoginUser {
  id: number;
  displayName: string;
  onboardingCompleted: boolean;
}

export interface LoginResponse {
  accessToken: string;
  expiresIn: number;
  user: LoginUser;
}

export interface MeResponse {
  id: number;
  displayName: string;
  onboardingCompleted: boolean;
}

export interface ImportantPerson {
  name: string;
  relation: string;
  note?: string;
}

export interface LifeProfileResponse {
  userId: number;
  currentSituation: string | null;
  focusAreas: string[];
  importantPeople: ImportantPerson[];
  preferences: Record<string, string>;
  profileVersion: number;
}

export interface GoalResponse {
  id: number;
  category: string;
  title: string;
  description: string;
  targetTimeText: string | null;
  details: Record<string, unknown>;
  status: string;
  createdAt: string;
}

export interface FollowupQuestion {
  id: string;
  question: string;
  inputType: string;
  optional: boolean;
}

export interface TaskResponse {
  id: number;
  status: string;
  progress: number;
  storyId: number | null;
  audioStatus: string | null;
  retryCount: number;
  error: { stage: string; code: string; message: string } | null;
}

export interface StoryAudio {
  status: string;
  url: string | null;
  durationMs: number | null;
  format: string | null;
}

export interface StoryResponse {
  id: number;
  title: string;
  summary: string;
  content: string;
  aiGenerated: boolean;
  favorite: boolean;
  audio: StoryAudio | null;
  provider: string;
  model: string;
  promptTemplateId: number;
  profileVersion: number;
  createdAt: string;
}
