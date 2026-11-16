/** pill-chip — 胶囊选项：label 文案；selected 选中态；small 小号（trending 行）。
 * 事件：tap（携带 { label }）。 */
Component({
  properties: {
    label: { type: String, value: "" },
    selected: { type: Boolean, value: false },
    small: { type: Boolean, value: false },
  },
  methods: {
    onTap() {
      this.triggerEvent("tap", { label: this.properties.label });
    },
  },
})
