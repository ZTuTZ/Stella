package com.futureself.home.api;

import com.futureself.story.api.StoryResponse;

import java.util.List;

/**
 * 首页聚合响应（GET /api/v1/home，实施文档 §12.7）：
 * 昵称 + 今日/最近故事 + 今日 Affirmations + 最近故事列表 + 是否存在运行中任务。
 */
public record HomeResponse(
        UserBrief user,
        StoryResponse todayStory,
        List<StoryResponse> recentStories,
        AffirmationBrief affirmations,
        boolean hasRunningTask) {

    public record UserBrief(long id, String displayName) {
    }

    public record AffirmationBrief(String date, List<String> items) {
    }
}
