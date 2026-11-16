/** manifest-input — 愿望输入：value 外部预填（同步到 innerValue）；placeholder 可换。
 * 事件：change { value }（实时）、submit { value }（发送钮/键盘确认，空值不触发）。 */
Component({
  properties: {
    value: {
      type: String,
      value: "",
      observer(newVal: string) {
        this.setData({ innerValue: newVal });
      },
    },
    placeholder: { type: String, value: "输入你此刻最想实现的事……" },
  },
  data: {
    innerValue: "",
    focused: false,
  },
  methods: {
    onInput(e: WechatMiniprogram.Input) {
      this.setData({ innerValue: e.detail.value });
      this.triggerEvent("change", { value: e.detail.value });
    },
    onFocus() {
      this.setData({ focused: true });
    },
    onBlur() {
      this.setData({ focused: false });
    },
    onSubmit() {
      const value = (this.data.innerValue || "").trim();
      if (!value) {
        return;
      }
      this.triggerEvent("submit", { value });
    },
  },
})
