package com.futureself.story.api;

/**
 * 故事详情响应（GET /api/v1/stories/{id}，实施文档 §12.5）。
 * audio V1 Phase 4 为 null（音频字段随 Phase 5 接入）；额外暴露回溯字段（§7.1）。
 */
public record StoryResponse(
        Long id,
        String title,
        String summary,
        String content,
        boolean aiGenerated,
        boolean favorite,
        Object audio,
        String provider,
        String model,
        Long promptTemplateId,
        int profileVersion,
        String createdAt) {
}
