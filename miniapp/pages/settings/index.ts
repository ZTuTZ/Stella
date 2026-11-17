/**
 * 设置页（§42.5）：产品定位说明（§19.1 想象/自我激励，不预测不保证）、AI 标识说明、退出登录。
 * 同一套柔和视觉系统。
 */
import { clearSession } from "../../stores/session";
import { goBackOrHome } from "../../utils/navigation";

Page({
  goBack() {
    goBackOrHome();
  },

  onLogout() {
    wx.showModal({
      title: "退出登录",
      content: "将清除本机登录数据（故事数据仍保留在服务端）",
      confirmText: "退出",
      confirmColor: "#C85D72",
      success: (res) => {
        if (res.confirm) {
          clearSession();
          wx.reLaunch({ url: "/pages/launch/index" });
        }
      },
    });
  },
})
