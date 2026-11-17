/**
 * 安全返回：页面栈 >1 时回退；栈底（如 redirectTo 进入的最终页）则回首页 Tab。
 * 解决「生成完成后 redirectTo 到故事详情，栈内只有该页，navigateBack 无效」的问题。
 */
export function goBackOrHome(): void {
  const pages = getCurrentPages();
  if (pages.length > 1) {
    wx.navigateBack();
    return;
  }
  wx.switchTab({ url: "/pages/home/index" });
}
