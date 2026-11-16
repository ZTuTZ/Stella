/**
 * API 基础配置。
 * 开发环境：微信开发者工具需勾选「不校验合法域名」；后端默认 8080。
 * 生产环境：改为 https 域名并在小程序后台配置合法域名。
 */
export const API_BASE_URL = "http://localhost:8080";

/** 请求超时（ms，§31.1） */
export const REQUEST_TIMEOUT = 15000;
