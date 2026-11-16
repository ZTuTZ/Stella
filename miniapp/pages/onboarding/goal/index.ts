/**
 * Onboarding 步骤 C（§5.2）：分类 → 标题（2~80）→ 描述（10~1200）→ 时间（可跳过）。
 * 最后一步 POST /goals 创建目标 → 跳 AI 追问页。
 */
import { goalApi } from "../../../services/api";
import { loadDraft, saveDraft } from "../../../stores/onboarding";

const CATEGORIES = ["事业", "财富", "爱情", "家庭", "自信", "健康生活方式", "学习成长", "旅行", "其他"];
const PROGRESS = ["5 / 6", "5 / 6", "5 / 6", "5 / 6"];

Page({
  data: {
    step: 1,
    progressText: "5 / 6",
    categories: CATEGORIES,
    draft: loadDraft(),
    error: "",
    loading: false,
  },

  onPickCategory(e: WechatMiniprogram.CustomEvent<{ label: string }>) {
    const area = String(e.currentTarget.dataset.area || e.detail.label || "");
    this.setData({ "draft.goalCategory": area, error: "" });
  },
  onTitleInput(e: WechatMiniprogram.Input) {
    this.setData({ "draft.goalTitle": e.detail.value, error: "" });
  },
  onDescriptionInput(e: WechatMiniprogram.Input) {
    this.setData({ "draft.goalDescription": e.detail.value, error: "" });
  },
  onTimeInput(e: WechatMiniprogram.Input) {
    this.setData({ "draft.targetTimeText": e.detail.value });
  },

  goBack() {
    if (this.data.step > 1) {
      this.setData({ step: this.data.step - 1, progressText: "5 / 6", error: "" });
      return;
    }
    wx.navigateBack();
  },

  onNext() {
    const d = this.data.draft;
    if (this.data.step === 1) {
      if (!d.goalCategory) {
        this.setData({ error: "先选一个方向" });
        return;
      }
      saveDraft({ goalCategory: d.goalCategory });
      this.advance();
      return;
    }
    if (this.data.step === 2) {
      const title = d.goalTitle.trim();
      if (title.length < 2 || title.length > 80) {
        this.setData({ error: "标题需要 2~80 个字" });
        return;
      }
      saveDraft({ goalTitle: title });
      this.advance();
      return;
    }
    if (this.data.step === 3) {
      const desc = d.goalDescription.trim();
      if (desc.length < 10 || desc.length > 1200) {
        this.setData({ error: "描述需要 10~1200 个字" });
        return;
      }
      saveDraft({ goalDescription: desc });
      this.advance();
      return;
    }
    this.createGoal();
  },

  advance() {
    const next = this.data.step + 1;
    this.setData({ step: next, progressText: PROGRESS[next - 1] || "5 / 6", error: "" });
  },

  createGoal() {
    const d = loadDraft();
    const targetTimeText = this.data.draft.targetTimeText.trim();
    saveDraft({ targetTimeText });
    this.setData({ loading: true });
    goalApi.create({
      goalCategory: d.goalCategory,
      goalTitle: d.goalTitle,
      goalDescription: d.goalDescription,
      targetTimeText: targetTimeText || undefined,
    }).then((goal) => {
      saveDraft({ goalId: goal.id });
      this.setData({ loading: false });
      wx.redirectTo({ url: "/pages/onboarding/followup/index?goalId=" + goal.id });
    }).catch(() => {
      this.setData({ loading: false });
    });
  },
})
