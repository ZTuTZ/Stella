/** 状态/枚举 → 中文标签与 Element Plus 颜色类型 */

export type TagType = "success" | "info" | "warning" | "danger" | "primary";

interface Labeled {
  label: string;
  type: TagType;
}

function pick(map: Record<string, Labeled>, value: string | null | undefined): Labeled {
  if (!value) {
    return { label: "—", type: "info" };
  }
  return map[value] || { label: value, type: "info" };
}

const TASK_STATUS: Record<string, Labeled> = {
  PENDING: { label: "等待中", type: "info" },
  TEXT_GENERATING: { label: "生成文本中", type: "primary" },
  TEXT_MODERATING: { label: "内容审核中", type: "warning" },
  TEXT_READY: { label: "文本就绪", type: "success" },
  AUDIO_SUBMITTING: { label: "提交音频", type: "primary" },
  AUDIO_PROCESSING: { label: "音频生成中", type: "warning" },
  AUDIO_STORING: { label: "音频转存中", type: "warning" },
  COMPLETED: { label: "已完成", type: "success" },
  RETRY_WAIT: { label: "等待重试", type: "warning" },
  FAILED: { label: "失败", type: "danger" },
  BLOCKED: { label: "已拦截", type: "danger" },
};

const GOAL_STATUS: Record<string, Labeled> = {
  ACTIVE: { label: "进行中", type: "success" },
  ARCHIVED: { label: "已归档", type: "info" },
  DELETED: { label: "已删除", type: "info" },
};

const USER_STATUS: Record<string, Labeled> = {
  ACTIVE: { label: "正常", type: "success" },
  DISABLED: { label: "已封禁", type: "danger" },
};

const STORY_STATUS: Record<string, Labeled> = {
  ACTIVE: { label: "正常", type: "success" },
  DELETED: { label: "已删除", type: "info" },
  BLOCKED: { label: "已拦截", type: "danger" },
};

const MODERATION_STATUS: Record<string, Labeled> = {
  PENDING: { label: "待审核", type: "warning" },
  PASSED: { label: "审核通过", type: "success" },
  BLOCKED: { label: "审核拦截", type: "danger" },
};

const PROVIDER_TYPE: Record<string, Labeled> = {
  TEXT: { label: "文本", type: "primary" },
  AUDIO: { label: "音频", type: "success" },
  MODERATION: { label: "审核", type: "warning" },
};

const ROLE: Record<string, Labeled> = {
  USER: { label: "用户", type: "info" },
  ADMIN: { label: "管理员", type: "warning" },
};

const OPERATION: Record<string, Labeled> = {
  FOLLOWUP: { label: "追问生成", type: "primary" },
  STORY: { label: "故事生成", type: "success" },
  AFFIRMATION: { label: "肯定句生成", type: "primary" },
  AUDIO_SUBMIT: { label: "音频提交", type: "success" },
  AUDIO_QUERY: { label: "音频查询", type: "info" },
  GOAL_CREATED: { label: "创建目标", type: "info" },
};

export function taskStatus(value?: string | null): Labeled {
  return pick(TASK_STATUS, value);
}

export function goalStatus(value?: string | null): Labeled {
  return pick(GOAL_STATUS, value);
}

export function userStatus(value?: string | null): Labeled {
  return pick(USER_STATUS, value);
}

export function storyStatus(value?: string | null): Labeled {
  return pick(STORY_STATUS, value);
}

export function moderationStatus(value?: string | null): Labeled {
  return pick(MODERATION_STATUS, value);
}

export function providerType(value?: string | null): Labeled {
  return pick(PROVIDER_TYPE, value);
}

export function roleLabel(value?: string | null): Labeled {
  return pick(ROLE, value);
}

export function operationLabel(value?: string | null): Labeled {
  return pick(OPERATION, value);
}

export function resultLabel(success: boolean): Labeled {
  return success ? { label: "成功", type: "success" } : { label: "失败", type: "danger" };
}

export function onboardingLabel(done: boolean): Labeled {
  return done ? { label: "已完成", type: "success" } : { label: "未完成", type: "info" };
}

/** 任务状态下拉选项（中文） */
export const TASK_STATUS_OPTIONS = Object.keys(TASK_STATUS).map((value) => ({
  value,
  label: TASK_STATUS[value].label,
}));

export const PROVIDER_TYPE_OPTIONS = Object.keys(PROVIDER_TYPE).map((value) => ({
  value,
  label: PROVIDER_TYPE[value].label,
}));

export const GOAL_STATUS_OPTIONS = Object.keys(GOAL_STATUS).map((value) => ({
  value,
  label: GOAL_STATUS[value].label,
}));

export const STORY_STATUS_OPTIONS = Object.keys(STORY_STATUS).map((value) => ({
  value,
  label: STORY_STATUS[value].label,
}));
