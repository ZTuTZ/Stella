/** 时间/数字格式化工具 */

function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

/** ISO 字符串 → 本地 YYYY-MM-DD HH:mm:ss；空值显示 — */
export function fmtDateTime(iso?: string | null): string {
  if (!iso) {
    return "—";
  }
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) {
    return iso;
  }
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

/** 毫秒 → "850 ms" / "1.2 s" */
export function fmtDuration(ms?: number | null): string {
  if (ms === null || ms === undefined) {
    return "—";
  }
  if (ms < 1000) {
    return `${ms} ms`;
  }
  return `${(ms / 1000).toFixed(1)} s`;
}

/** 数值/缺失值展示 */
export function fmtValue(v?: number | string | null): string {
  if (v === null || v === undefined || v === "") {
    return "—";
  }
  return String(v);
}
