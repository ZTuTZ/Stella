/**
 * 我的（占位，UI Phase 4 按 §41.6.9 实装：昵称 + 3~5 个大块浅色入口）。
 */
import { meApi } from "../../services/api";
import { MeResponse } from "../../types/api";

Page({
  data: {
    displayName: "",
  },

  onShow() {
    const tabBar = this.getTabBar();
    if (tabBar) {
      tabBar.setData({ selected: 1 });
    }
    meApi.me({ silent: true }).then((me: MeResponse) => {
      this.setData({ displayName: me.displayName });
    }).catch(() => {
      // 未登录/网络问题保持占位文案
    });
  },
})
