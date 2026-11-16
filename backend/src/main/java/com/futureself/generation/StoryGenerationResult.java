package com.futureself.generation;

/**
 * STORY 结构化输出（实施文档 §14.2）。
 */
public record StoryGenerationResult(
        String title,
        String summary,
        String content,
        AudioDirection audioDirection) {

    public record AudioDirection(String mood, String pace, String notes) {
    }
}
