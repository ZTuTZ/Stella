package com.futureself.affirmation.api;

/**
 * 每日肯定句响应（GET /api/v1/affirmations/today，实施文档 §12.6/§14.3：6 条）。
 */
public record AffirmationResponse(String date, java.util.List<String> items) {
}
