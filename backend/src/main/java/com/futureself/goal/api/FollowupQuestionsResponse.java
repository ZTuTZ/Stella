package com.futureself.goal.api;

import java.util.List;

/**
 * AI 追问响应（POST /api/v1/goals/{id}/followup-questions，实施文档 §12.3）。
 */
public record FollowupQuestionsResponse(List<QuestionItem> questions) {

    /** inputType V1 固定 TEXT。 */
    public record QuestionItem(String id, String question, String inputType, boolean optional) {
    }
}
