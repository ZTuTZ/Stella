/** primary-button — 主按钮：text 文案；disabled 置灰；loading 显示加载圈。
 * 事件：tap（disabled/loading 时不触发）。 */
Component({
  properties: {
    text: { type: String, value: "继续" },
    disabled: { type: Boolean, value: false },
    loading: { type: Boolean, value: false },
  },
  methods: {
    onTap() {
      if (this.properties.disabled || this.properties.loading) {
        return;
      }
      this.triggerEvent("tap", {});
    },
  },
})
