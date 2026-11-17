/**
 * API 基础配置（H-10 §11.4）。
 * - development：默认本机后端（开发者工具勾选「不校验合法域名」）
 * - trial / release：必须显式填写真实 https 域名，未填写时请求会直接失败并提示，
 *   绝不静默回落到 localhost。
 * 所有 API 地址只在此处维护。
 */
type MiniProgramEnv = "develop" | "trial" | "release";

function currentEnv(): MiniProgramEnv {
  try {
    const env = wx.getAccountInfoSync().miniProgram.envVersion;
    if (env === "trial" || env === "release") {
      return env;
    }
    return "develop";
  } catch (e) {
    return "develop";
  }
}

const DEVELOPMENT_BASE_URL = "http://localhost:8080";
/** TODO(发布前)：填写正式 https 域名（需加入小程序合法域名） */
const TRIAL_BASE_URL = "";
/** TODO(发布前)：填写正式 https 域名（需加入小程序合法域名） */
const RELEASE_BASE_URL = "";

const ENV: MiniProgramEnv = currentEnv();

export const API_BASE_URL =
  ENV === "release" ? RELEASE_BASE_URL : ENV === "trial" ? TRIAL_BASE_URL : DEVELOPMENT_BASE_URL;

export const API_CONFIGURED = API_BASE_URL.length > 0;

/** 请求前统一校验：未配置正式域名时给出明确错误，避免误请求 localhost。 */
export function assertApiConfigured(): void {
  if (!API_CONFIGURED) {
    throw new Error("未配置 API 域名（" + ENV + "），请在 services/config.ts 填写正式 https 域名");
  }
}

/** 请求超时（ms，§31.1） */
export const REQUEST_TIMEOUT = 15000;
