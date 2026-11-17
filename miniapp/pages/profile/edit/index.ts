/**
 * 编辑人生档案（§9.2/§32.2）：昵称 + 领域(1~5) + 当前生活 + 重要人物(0~8)。
 * 保存：PATCH /me（昵称）+ PUT life-profile（全量，含既有 preferences 防误清）。
 * profile_version 由后端自增；只影响后续生成（§32.3）。
 */
import { meApi, profileApi } from "../../../services/api";
import { goBackOrHome } from "../../../utils/navigation";
import { ImportantPerson, LifeProfileResponse, MeResponse } from "../../../types/api";
import { getStatusBarHeight } from "../../../utils/safeArea";

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
    statusBarHeight: getStatusBarHeight(),
    displayName: "",
    focusOptions: [] as FocusOption[],
    focusAreas: [] as string[],
    currentSituation: "",
    importantPeople: [] as ImportantPerson[],
    preferences: {} as Record<string, string>,
    adding: false,
    form: { name: "", relation: "", note: "" },
    saving: false,
    nameError: "",
    focusError: "",
  },

  onLoad() {
    Promise.all([
      meApi.me({ silent: true }),
      profileApi.get(),
    ]).then(([me, profile]: [MeResponse, LifeProfileResponse]) => {
      this.setData({
        displayName: me.displayName,
        focusAreas: profile.focusAreas || [],
        focusOptions: buildFocusOptions(profile.focusAreas || []),
        currentSituation: profile.currentSituation || "",
        importantPeople: profile.importantPeople || [],
        preferences: profile.preferences || {},
      });
    }).catch(() => {
      // toast 已由底层处理
    });
  },

  onNameInput(e: WechatMiniprogram.Input) {
    this.setData({ displayName: e.detail.value, nameError: "" });
  },

  onToggleFocus(e: WechatMiniprogram.CustomEvent<{ label: string }>) {
    const area = String(e.currentTarget.dataset.area || e.detail.label || "");
    const list = this.data.focusAreas.slice();
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
    this.setData({ focusAreas: list, focusOptions: buildFocusOptions(list), focusError: "" });
  },

  onSituationInput(e: WechatMiniprogram.Input) {
    this.setData({ currentSituation: e.detail.value });
  },

  // —— 重要人物编辑 ——
  onStartAdd() {
    if (this.data.importantPeople.length >= 8) {
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
    this.setData({ importantPeople: this.data.importantPeople.concat([person]), adding: false });
  },
  onRemovePerson(e: WechatMiniprogram.TouchEvent) {
    const index = Number(e.currentTarget.dataset.index);
    const list = this.data.importantPeople.slice();
    list.splice(index, 1);
    this.setData({ importantPeople: list });
  },

  goBack() {
    goBackOrHome();
  },

  onSave() {
    const name = this.data.displayName.trim();
    if (!name) {
      this.setData({ nameError: "昵称不能为空" });
      return;
    }
    if (this.data.focusAreas.length < 1) {
      this.setData({ focusError: "至少保留一个关注领域" });
      return;
    }
    if (this.data.adding) {
      wx.showToast({ title: "先保存或取消编辑中的人物", icon: "none" });
      return;
    }
    this.setData({ saving: true, nameError: "", focusError: "" });
    Promise.all([
      meApi.updateDisplayName(name),
      profileApi.update({
        focusAreas: this.data.focusAreas,
        currentSituation: this.data.currentSituation.trim(),
        importantPeople: this.data.importantPeople,
        preferences: this.data.preferences,
      }),
    ]).then(() => {
      this.setData({ saving: false });
      wx.showToast({ title: "档案已更新", icon: "success", duration: 1400 });
      setTimeout(() => wx.navigateBack(), 800);
    }).catch(() => {
      this.setData({ saving: false });
    });
  },
})