package com.futureself.story;

import com.futureself.audio.domain.AudioAsset;
import com.futureself.audio.domain.AudioAssetRepository;
import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.story.api.StoryResponse;
import com.futureself.story.domain.Story;
import com.futureself.story.domain.StoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 故事服务（实施文档 §9.4）。Phase 4：创建（供生成链路）+ 详情。
 * 列表/收藏/删除在 Phase 7；音频响应与重试在 Phase 5 接入。
 */
@Service
public class StoryService {

    private final StoryRepository storyRepository;
    private final AudioAssetRepository audioAssetRepository;

    public StoryService(StoryRepository storyRepository, AudioAssetRepository audioAssetRepository) {
        this.storyRepository = storyRepository;
        this.audioAssetRepository = audioAssetRepository;
    }

    @Transactional(readOnly = true)
    public StoryResponse getOwnStory(long storyId, long userId) {
        Story story = storyRepository
                .findByIdAndUserIdAndStatus(storyId, userId, Story.STATUS_ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORY_NOT_FOUND));
        return toResponse(story);
    }

    /** 当前有效音频（§32.5）：非 SUPERSEDED 的最新一条；无音频时为 null。 */
    public Map<String, Object> audioOf(Story story) {
        return audioAssetRepository
                .findTopByStoryIdAndStatusNotOrderByIdDesc(story.getId(), AudioAsset.STATUS_SUPERSEDED)
                .map(asset -> {
                    Map<String, Object> audio = new LinkedHashMap<>();
                    audio.put("status", asset.getStatus());
                    audio.put("url", asset.getPublicUrl()); // READY 时才有值（自有存储 URL）
                    audio.put("durationMs", asset.getDurationMs());
                    audio.put("format", asset.getFormat());
                    return audio;
                })
                .orElse(null);
    }

    public StoryResponse toResponse(Story story) {
        return new StoryResponse(
                story.getId(),
                story.getTitle(),
                story.getSummary(),
                story.getContent(),
                true, // AI 生成内容标识（§19.4）
                story.isFavorite(),
                audioOf(story),
                story.getProvider(),
                story.getModel(),
                story.getPromptTemplateId(),
                story.getProfileVersion(),
                story.getCreatedAt() == null ? null : story.getCreatedAt().toString());
    }
}
