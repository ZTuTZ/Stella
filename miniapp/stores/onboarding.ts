/**
 * Onboarding 草稿（§26.3：中途退出再进入可恢复）。
 * 各步骤完成即写入 storage；basic/context 步骤同时提交后端。
 */
import { ImportantPerson } from "../types/api";

export interface OnboardingDraft {
  displayName: string;
  focusAreas: string[];
  currentSituation: string;
  importantPeople: ImportantPerson[];
  goalCategory: string;
  goalTitle: string;
  goalDescription: string;
  targetTimeText: string;
  goalId: number | null;
}

const KEY = "fs.onboarding.draft";

export function emptyDraft(): OnboardingDraft {
  return {
    displayName: "",
    focusAreas: [],
    currentSituation: "",
    importantPeople: [],
    goalCategory: "",
    goalTitle: "",
    goalDescription: "",
    targetTimeText: "",
    goalId: null,
  };
}

export function loadDraft(): OnboardingDraft {
  const stored = wx.getStorageSync(KEY) as OnboardingDraft | "";
  if (!stored) {
    return emptyDraft();
  }
  return { ...emptyDraft(), ...stored };
}

export function saveDraft(patch: Partial<OnboardingDraft>): OnboardingDraft {
  const draft = { ...loadDraft(), ...patch };
  wx.setStorageSync(KEY, draft);
  return draft;
}

export function clearDraft(): void {
  wx.removeStorageSync(KEY);
}
