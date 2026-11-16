package com.futureself.moderation;

/**
 * 审核结果：blocked + 分类 + 原因（原因面向内部日志，不直接透出用户）。
 */
public record ModerationResult(boolean blocked, String category, String reason) {

    public static ModerationResult pass() {
        return new ModerationResult(false, null, null);
    }

    public static ModerationResult block(String category, String reason) {
        return new ModerationResult(true, category, reason);
    }
}
