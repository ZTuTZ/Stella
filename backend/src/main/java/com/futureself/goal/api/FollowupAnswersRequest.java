package com.futureself.goal.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 保存追问回答（PUT /api/v1/goals/{id}/followup-answers，实施文档 §12.3）。
 * 回答写入 goal.details_json（§5.2 步骤 D）。
 */
public record FollowupAnswersRequest(
        @Valid List<AnswerItem> answers) {

    public record AnswerItem(
            @NotBlank(message = "questionId is required") String questionId,
            @Size(max = 500, message = "answer must be <= 500 chars") String answer) {
    }
}
