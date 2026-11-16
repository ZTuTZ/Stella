/**
 * 会话与登录（实施文档 §5.1/§18/§31.1）。
 * token 存本地 storage；401 清理后重新登录（由 request client 调用）。
 *
 * 开发身份说明：后端 WECHAT_MOCK_LOGIN=true 时，openid = mock_sha256(code)。
 * 真实 wx.login 每次返回不同 code → 会生成不同用户。因此非 release 环境固定
 * 使用 'devtools-stable-user' 作为 code，保证开发者工具里“再次进入数据仍在”。
 * release 包走真实 wx.login（后端此时应配置 WECHAT_MOCK_LOGIN=false + 真实密钥）。
 */
import { LoginResponse, LoginUser } from "../types/api";
import { API_BASE_URL, REQUEST_TIMEOUT } from "../services/config";

const TOKEN_KEY = "fs.token";
const USER_KEY = "fs.user";

let cachedToken = "";
let cachedUser: LoginUser | null = null;

export function getToken(): string {
  if (!cachedToken) {
    cachedToken = (wx.getStorageSync(TOKEN_KEY) as string) || "";
  }
  return cachedToken;
}

export function getUser(): LoginUser | null {
  if (!cachedUser) {
    cachedUser = (wx.getStorageSync(USER_KEY) as LoginUser) || null;
  }
  return cachedUser;
}

export function setSession(token: string, user: LoginUser): void {
  cachedToken = token;
  cachedUser = user;
  wx.setStorageSync(TOKEN_KEY, token);
  wx.setStorageSync(USER_KEY, user);
}

export function clearSession(): void {
  cachedToken = "";
  cachedUser = null;
  wx.removeStorageSync(TOKEN_KEY);
  wx.removeStorageSync(USER_KEY);
}

function loginCode(): Promise<string> {
  try {
    const env = wx.getAccountInfoSync().miniProgram.envVersion;
    if (env !== "release") {
      return Promise.resolve("devtools-stable-user");
    }
  } catch (e) {
    // 取不到环境信息按真实流程走
  }
  return new Promise((resolve, reject) => {
    wx.login({
      success: (res) => (res.code ? resolve(res.code) : reject(new Error("wx.login 无 code"))),
      fail: () => reject(new Error("wx.login 失败")),
    });
  });
}

/**
 * 静默登录：wx.login → /api/v1/auth/wechat/login → 保存会话。
 * force=true 时即使已有 token 也重新登录（401 恢复路径）。
 */
export function ensureLogin(force = false): Promise<LoginUser> {
  if (!force && getToken()) {
    const user = getUser();
    if (user) {
      return Promise.resolve(user);
    }
  }
  return loginCode().then((code) => {
    // 登录接口本身不需要鉴权，走底层 request 避免循环依赖
    return new Promise<LoginResponse>((resolve, reject) => {
      wx.request({
        url: API_BASE_URL + "/api/v1/auth/wechat/login",
        method: "POST",
        data: { code },
        timeout: REQUEST_TIMEOUT,
        header: { "Content-Type": "application/json" },
        success: (res) => {
          const body = res.data as { success: boolean; data?: LoginResponse; code?: string; message?: string };
          if (res.statusCode === 200 && body.success && body.data) {
            resolve(body.data);
          } else {
            reject({ code: body.code || "WECHAT_LOGIN_FAILED", message: body.message || "登录失败" });
          }
        },
        fail: () => reject({ code: "NETWORK", message: "网络异常，请重试" }),
      });
    });
  }).then((resp) => {
    setSession(resp.accessToken, resp.user);
    return resp.user;
  });
}
