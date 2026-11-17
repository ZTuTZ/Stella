/** pill-chip — 胶囊选项：label 文案；selected 选中态；small 小号（trending 行）。
 * 事件：select（携带 { label }；内部 catchtap 防止原生 tap 冒泡导致父级重复处理）。 */
Component({
  properties: {
    label: { type: String, value: "" },
    selected: { type: Boolean, value: false },
    small: { type: Boolean, value: false },
  },
  methods: {
    onTap() {
      this.triggerEvent("select", { label: this.properties.label });
    },
  },
})
