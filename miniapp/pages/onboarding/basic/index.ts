/**
 * Onboarding 步骤 A（§5.2）：昵称（1~30）→ 关注领域（1~5，白名单）。
 * 数据先落本地草稿；第二步「保存并继续」时提交 PATCH /me + PUT life-profile。
 */
import { goBackOrHome } from "../../../utils/navigation";
import { meApi, profileApi } from "../../../services/api";
import { loadDraft, saveDraft } from "../../../stores/onboarding";
import { OnboardingDraft } from "../../../stores/onboarding";

const FOCUS_OPTIONS = ["事业", "财富", "爱情", "家庭", "自信", "健康生活方式", "学习成长", "旅行", "其他"];

interface FocusOption {
  label: string;
  selected: boolean;
}

function buildFocusOptions(selectedAreas: string[]): FocusOption[] {
  return FOCUS_OPTIONS.map((label) => ({ label, selected: selectedAreas.indexOf(label) > -1 }));
}

Page({
  data: {
    step: 1,
    progressText: "1 / 6",
    focusOptions: buildFocusOptions(loadDraft().focusAreas),
    draft: loadDraft() as OnboardingDraft,
    nameError: "",
    focusError: "",
    loading: false,
  },

  onNameInput(e: WechatMiniprogram.Input) {
    this.setData({ "draft.displayName": e.detail.value, nameError: "" });
  },

  onToggleFocus(e: WechatMiniprogram.CustomEvent<{ label: string }>) {
    const area = String(e.currentTarget.dataset.area || e.detail.label || "");
    const list = this.data.draft.focusAreas.slice();
    const idx = list.indexOf(area);
    if (idx > -1) {
      list.splice(idx, 1);
    } else {
      if (list.length >= 5) {
        wx.showToast({ title: "最多选择 5 个", icon: "none" });
        return;
      }
      list.push(area);
    }
    this.setData({
      "draft.focusAreas": list,
      focusOptions: buildFocusOptions(list),
      focusError: "",
    });
  },

  goBack() {
    if (this.data.step === 2) {
      this.setData({ step: 1, progressText: "1 / 6" });
      return;
    }
    goBackOrHome();
  },

  onNext() {
    if (this.data.step === 1) {
      const name = this.data.draft.displayName.trim();
      if (!name) {
        this.setData({ nameError: "先告诉我怎么称呼你吧" });
        return;
      }
      saveDraft({ displayName: name });
      this.setData({ step: 2, progressText: "2 / 6" });
      return;
    }
    this.saveStepTwo();
  },

  saveStepTwo() {
    const focusAreas = this.data.draft.focusAreas;
    if (focusAreas.length < 1) {
      this.setData({ focusError: "至少选择一个方向" });
      return;
    }
    this.setData({ loading: true });
    const draft = saveDraft({ focusAreas });
    meApi.updateDisplayName(draft.displayName).then(() =>
      profileApi.update({ focusAreas })
    ).then(() => {
      this.setData({ loading: false });
      wx.redirectTo({ url: "/pages/onboarding/context/index" });
    }).catch(() => {
      this.setData({ loading: false });
    });
  },
})