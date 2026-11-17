/**
 * Onboarding 步骤 B（§5.2）：当前生活（0~1000，可跳过）→ 重要人物（0~8，可跳过）。
 * 保存时全量 PUT life-profile（含 basic 已存的 focusAreas，保持草稿一致性）。
 */
import { goBackOrHome } from "../../../utils/navigation";
import { profileApi } from "../../../services/api";
import { ImportantPerson } from "../../../types/api";
import { loadDraft, saveDraft } from "../../../stores/onboarding";

Page({
  data: {
    step: 1,
    progressText: "3 / 6",
    draft: loadDraft(),
    adding: false,
    form: { name: "", relation: "", note: "" },
    loading: false,
  },

  onSituationInput(e: WechatMiniprogram.Input) {
    this.setData({ "draft.currentSituation": e.detail.value });
  },

  goBack() {
    if (this.data.step === 2) {
      this.setData({ step: 1, progressText: "3 / 6" });
      return;
    }
    goBackOrHome();
  },

  // —— 重要人物编辑 ——
  onStartAdd() {
    if (this.data.draft.importantPeople.length >= 8) {
      wx.showToast({ title: "最多添加 8 位", icon: "none" });
      return;
    }
    this.setData({ adding: true, form: { name: "", relation: "", note: "" } });
  },
  onCancelAdd() {
    this.setData({ adding: false });
  },
  onFormInput(e: WechatMiniprogram.Input) {
    const field = String(e.currentTarget.dataset.field) as "name" | "relation" | "note";
    this.setData({ [`form.${field}`]: e.detail.value });
  },
  onConfirmAdd() {
    const { name, relation } = this.data.form;
    if (!name.trim() || !relation.trim()) {
      wx.showToast({ title: "称呼和关系都要填哦", icon: "none" });
      return;
    }
    const person: ImportantPerson = {
      name: name.trim(),
      relation: relation.trim(),
      note: this.data.form.note.trim() || undefined,
    };
    const list = this.data.draft.importantPeople.concat([person]);
    saveDraft({ importantPeople: list });
    this.setData({ adding: false, "draft.importantPeople": list });
  },
  onRemovePerson(e: WechatMiniprogram.TouchEvent) {
    const index = Number(e.currentTarget.dataset.index);
    const list = this.data.draft.importantPeople.slice();
    list.splice(index, 1);
    saveDraft({ importantPeople: list });
    this.setData({ "draft.importantPeople": list });
  },

  onNext() {
    if (this.data.step === 1) {
      const text = this.data.draft.currentSituation.trim();
      saveDraft({ currentSituation: text });
      this.setData({ step: 2, progressText: "4 / 6", "draft.currentSituation": text });
      return;
    }
    this.save();
  },

  save() {
    if (this.data.adding) {
      wx.showToast({ title: "先保存或取消编辑中的人物", icon: "none" });
      return;
    }
    this.setData({ loading: true });
    const draft = loadDraft();
    profileApi.update({
      currentSituation: draft.currentSituation || undefined,
      focusAreas: draft.focusAreas,
      importantPeople: draft.importantPeople,
    }).then(() => {
      this.setData({ loading: false });
      wx.redirectTo({ url: "/pages/onboarding/goal/index" });
    }).catch(() => {
      this.setData({ loading: false });
    });
  },
})