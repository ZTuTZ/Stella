package com.futureself.story;

import com.futureself.audio.domain.AudioAsset;
import com.futureself.audio.domain.AudioAssetRepository;
import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.config.AppStorageProperties;
import com.futureself.storage.StorageService;
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
    private final StorageService storageService;
    private final AppStorageProperties storageProps;

    public StoryService(StoryRepository storyRepository,
                        AudioAssetRepository audioAssetRepository,
                        StorageService storageService,
                        AppStorageProperties storageProps) {
        this.storyRepository = storyRepository;
        this.audioAssetRepository = audioAssetRepository;
        this.storageService = storageService;
        this.storageProps = storageProps;
    }

    @Transactional(readOnly = true)
    public StoryResponse getOwnStory(long storyId, long userId) {
        Story story = storyRepository
                .findByIdAndUserIdAndStatus(storyId, userId, Story.STATUS_ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORY_NOT_FOUND));
        return toResponse(story);
    }

    /** 故事列表分页（§12.5）。favorite=true 仅收藏；默认全部（ACTIVE + PASSED）。按创建时间倒序。 */
    @Transactional(readOnly = true)
    public StoryPage list(long userId, int page, int size, boolean favoriteOnly) {
        var pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.desc("createdAt"),
                        org.springframework.data.domain.Sort.Order.desc("id")));
        var result = favoriteOnly
                ? storyRepository.findByUserIdAndStatusAndModerationStatusAndFavorite(
                        userId, Story.STATUS_ACTIVE, Story.MODERATION_PASSED, true, pageable)
                : storyRepository.findByUserIdAndStatusAndModerationStatus(
                        userId, Story.STATUS_ACTIVE, Story.MODERATION_PASSED, pageable);
        return new StoryPage(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /** 收藏/取消收藏（§12.5）。 */
    @Transactional
    public Map<String, Object> setFavorite(long storyId, long userId, boolean favorite) {
        Story story = storyRepository
                .findByIdAndUserIdAndStatus(storyId, userId, Story.STATUS_ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORY_NOT_FOUND));
        story.setFavorite(favorite);
        storyRepository.save(story);
        return Map.of("id", story.getId(), "favorite", story.isFavorite());
    }

    /** 软删除（§32.8）。 */
    @Transactional
    public Map<String, Object> deleteOwn(long storyId, long userId) {
        Story story = storyRepository
                .findByIdAndUserIdAndStatus(storyId, userId, Story.STATUS_ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORY_NOT_FOUND));
        story.setStatus(Story.STATUS_DELETED);
        storyRepository.save(story);
        return Map.of("deleted", true);
    }

    public record StoryPage(java.util.List<StoryResponse> items, int page, int size, long totalElements, int totalPages) {
    }

    /** 当前有效音频（§32.5）：非 SUPERSEDED 的最新一条；无音频时为 null。 */
    public Map<String, Object> audioOf(Story story) {
        // H-05：存在 READY 时优先返回（即使正在生成新音频），否则返回最新在途状态
        return audioAssetRepository
                .findTopByStoryIdAndStatusOrderByIdDesc(story.getId(), AudioAsset.STATUS_READY)
                .or(() -> audioAssetRepository.findTopByStoryIdAndStatusNotOrderByIdDesc(
                        story.getId(), AudioAsset.STATUS_SUPERSEDED))
                .map(asset -> {
                    Map<String, Object> audio = new LinkedHashMap<>();
                    audio.put("status", asset.getStatus());
                    // H-03：READY 时按需生成短时读取 URL；不返回数据库中长期 publicUrl
                    String url = AudioAsset.STATUS_READY.equals(asset.getStatus()) && asset.getStorageKey() != null
                            ? storageService.createReadUrl(asset.getStorageKey(),
                                    java.time.Duration.ofMinutes(storageProps.readUrlTtlMinutes()))
                            : null;
                    audio.put("url", url);
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
