package com.futureself.story;

import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.story.api.StoryResponse;
import com.futureself.story.domain.Story;
import com.futureself.story.domain.StoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 故事服务（实施文档 §9.4）。Phase 4：创建（供生成链路）+ 详情。
 * 列表/收藏/删除在 Phase 7；音频相关在 Phase 5。
 */
@Service
public class StoryService {

    private final StoryRepository storyRepository;

    public StoryService(StoryRepository storyRepository) {
        this.storyRepository = storyRepository;
    }

    @Transactional(readOnly = true)
    public StoryResponse getOwnStory(long storyId, long userId) {
        Story story = storyRepository
                .findByIdAndUserIdAndStatus(storyId, userId, Story.STATUS_ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORY_NOT_FOUND));
        return toResponse(story);
    }

    public StoryResponse toResponse(Story story) {
        return new StoryResponse(
                story.getId(),
                story.getTitle(),
                story.getSummary(),
                story.getContent(),
                true, // AI 生成内容标识（§19.4）
                story.isFavorite(),
                null, // audio：Phase 5 接入
                story.getProvider(),
                story.getModel(),
                story.getPromptTemplateId(),
                story.getProfileVersion(),
                story.getCreatedAt() == null ? null : story.getCreatedAt().toString());
    }
}
