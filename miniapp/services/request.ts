/**
 * 统一 request client（实施文档 §31.1）：
 * - baseURL + Authorization + X-Request-Id 透传
 * - 401 自动清理登录态并重新登录后重试一次
 * - 业务错误统一 Toast（silent 可关）
 * - 超时 15s；网络异常统一文案
 */
import { API_BASE_URL, REQUEST_TIMEOUT, assertApiConfigured } from "./config";
import { ApiError, ApiEnvelope } from "../types/api";
import { clearSession, ensureLogin, getToken } from "../stores/session";

export interface RequestOptions {
  /** 出错时是否弹统一 Toast（默认 true） */
  silent?: boolean;
  /** 401 时是否允许自动重登并重试（默认 true；登录接口本身必须 false 防循环） */
  retryOn401?: boolean;
}

let requestIdSeq = 0;

function newRequestId(): string {
  requestIdSeq += 1;
  return `wx-${Date.now().toString(36)}-${requestIdSeq.toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

function toast(message: string): void {
  wx.showToast({ title: message, icon: "none", duration: 2200 });
}

function fail(code: string, message: string, statusCode?: number): ApiError {
  return { code, message, statusCode };
}

function raw<T>(method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE", path: string, data?: unknown): Promise<T> {
  try {
    assertApiConfigured();
  } catch (e) {
    const message = e instanceof Error ? e.message : "API 未配置";
    toast(message);
    return Promise.reject(fail("API_NOT_CONFIGURED", message));
  }
  const requestId = newRequestId();
  const token = getToken();
  return new Promise<T>((resolve, reject) => {
    wx.request({
      url: API_BASE_URL + path,
      // 类型声明未包含 PATCH，但微信运行时实际支持；此处显式转换
      method: method as unknown as WechatMiniprogram.RequestOption["method"],
      data: data as string | WechatMiniprogram.IAnyObject | undefined,
      timeout: REQUEST_TIMEOUT,
      header: {
        "Content-Type": "application/json",
        "X-Request-Id": requestId,
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      success: (res) => {
        const body = res.data as ApiEnvelope<T> | undefined;
        if (res.statusCode === 200 && body && body.success) {
          resolve(body.data);
          return;
        }
        if (res.statusCode === 401) {
          reject(fail("AUTH_REQUIRED", "登录已过期", 401));
          return;
        }
        reject(fail(body?.code || "INTERNAL_ERROR", body?.message || "请求失败，请稍后重试", res.statusCode));
      },
      fail: () => reject(fail("NETWORK", "网络异常，请检查网络后重试")),
    });
  });
}

/** 请求入口：处理 401 重登重试与统一 Toast。 */
export function request<T>(method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE", path: string, data?: unknown, options?: RequestOptions): Promise<T> {
  const opts = options || {};
  const run = (): Promise<T> => raw<T>(method, path, data);

  return run().catch((err: ApiError) => {
    if (err.statusCode === 401 && opts.retryOn401 !== false) {
      clearSession();
      return ensureLogin(true).then(() => raw<T>(method, path, data));
    }
    throw err;
  }).catch((err: ApiError) => {
    if (!opts.silent) {
      toast(err.message);
    }
    throw err;
  });
}

export const api = {
  get: <T>(path: string, options?: RequestOptions) => request<T>("GET", path, undefined, options),
  post: <T>(path: string, data?: unknown, options?: RequestOptions) => request<T>("POST", path, data, options),
  put: <T>(path: string, data?: unknown, options?: RequestOptions) => request<T>("PUT", path, data, options),
  patch: <T>(path: string, data?: unknown, options?: RequestOptions) => request<T>("PATCH", path, data, options),
  delete: <T>(path: string, options?: RequestOptions) => request<T>("DELETE", path, undefined, options),
};
