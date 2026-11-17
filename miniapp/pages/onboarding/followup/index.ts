/**
 * Onboarding 步骤 D/E（§5.2）：AI 追问 0~3 题（可逐题跳过）→ 保存回答 → 创建生成任务 → 生成进度页。
 * Mock 文本 AI 固定返回 2 题；真实 Provider 下可能 0 题（直接生成）。
 */
import { goBackOrHome } from "../../../utils/navigation";
import { generationApi, goalApi, meApi } from "../../../services/api";
import { FollowupQuestion } from "../../../types/api";
import { clearDraft } from "../../../stores/onboarding";

Page({
  data: {
    goalId: 0,
    loading: true,
    questions: [] as FollowupQuestion[],
    current: 0,
    answers: [] as string[],
    optionalHint: "",
    submitting: false,
  },

  onLoad(options: Record<string, string | undefined>) {
    const goalId = Number(options.goalId || 0);
    this.setData({ goalId });
    goalApi.followupQuestions(goalId).then((res) => {
      this.setData({
        loading: false,
        questions: res.questions || [],
        answers: (res.questions || []).map(() => ""),
      });
      this.updateHint();
    }).catch(() => {
      this.setData({ loading: false });
    });
  },

  updateHint() {
    const q = this.data.questions[this.data.current];
    if (!q) {
      return;
    }
    this.setData({ optionalHint: q.optional ? "可回答可跳过" : "" });
  },

  onAnswerInput(e: WechatMiniprogram.Input) {
    const key = `answers[${this.data.current}]`;
    this.setData({ [key]: e.detail.value });
  },

  onSkip() {
    // 跳过 = 置空答案进入下一题
    const key = `answers[${this.data.current}]`;
    this.setData({ [key]: "" });
    this.nextQuestion();
  },

  nextQuestion() {
    const next = this.data.current + 1;
    this.setData({ current: next });
    this.updateHint();
  },

  goBack() {
    if (this.data.current > 0) {
      this.setData({ current: this.data.current - 1 });
      this.updateHint();
      return;
    }
    goBackOrHome();
  },

  onNext() {
    if (this.data.current < this.data.questions.length - 1) {
      this.nextQuestion();
      return;
    }
    if (this.data.current === this.data.questions.length - 1) {
      this.submitAnswers();
      return;
    }
    this.startGeneration();
  },

  submitAnswers() {
    const payload = this.data.questions
      .map((q, i) => ({ questionId: q.id, answer: (this.data.answers[i] || "").trim() }))
      .filter((a) => a.answer);
    if (payload.length === 0) {
      this.setData({ current: this.data.questions.length }); // 全部跳过 → 直接生成
      return;
    }
    this.setData({ submitting: true });
    goalApi.followupAnswers(this.data.goalId, payload).then(() => {
      this.setData({ submitting: false, current: this.data.questions.length });
    }).catch(() => {
      this.setData({ submitting: false });
    });
  },

  startGeneration() {
    this.setData({ submitting: true });
    generationApi.generateStory(this.data.goalId).then((res) => {
      // 标记 onboarding 完成（失败不影响进入生成流程）
      meApi.completeOnboarding({ silent: true }).catch(() => undefined).then(() => {
        clearDraft();
        wx.redirectTo({ url: "/pages/generation/progress/index?taskId=" + res.taskId });
      });
    }).catch(() => {
      this.setData({ submitting: false });
    });
  },
})