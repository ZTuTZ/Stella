package com.futureself.storage;

import com.futureself.audio.domain.AudioAsset;
import com.futureself.audio.domain.AudioAssetRepository;
import com.futureself.config.AppRetentionProperties;
import com.futureself.story.domain.Story;
import com.futureself.story.domain.StoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 音频物理清理（实施文档 §25.2）：每日 03:05 清理超过保留期的已删除故事音频文件。
 * 文件从 StorageService 删除；audio_asset 行保留（status=SUPERSEDED、存储字段清空）以便审计。
 */
@Component
public class AudioCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(AudioCleanupJob.class);

    private final StoryRepository storyRepository;
    private final AudioAssetRepository audioAssetRepository;
    private final StorageService storageService;
    private final AppRetentionProperties retentionProps;

    public AudioCleanupJob(StoryRepository storyRepository,
                           AudioAssetRepository audioAssetRepository,
                           StorageService storageService,
                           AppRetentionProperties retentionProps) {
        this.storyRepository = storyRepository;
        this.audioAssetRepository = audioAssetRepository;
        this.storageService = storageService;
        this.retentionProps = retentionProps;
    }

    @Scheduled(cron = "0 5 3 * * *", zone = "Asia/Shanghai")
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionProps.deletedAudioDays());
        List<Story> expired = storyRepository.findByStatusAndUpdatedAtBefore(Story.STATUS_DELETED, cutoff);
        int filesDeleted = 0;
        for (Story story : expired) {
            List<AudioAsset> assets = audioAssetRepository.findByStoryId(story.getId());
            for (AudioAsset asset : assets) {
                if (AudioAsset.STATUS_SUPERSEDED.equals(asset.getStatus()) || asset.getStorageKey() == null) {
                    continue;
                }
                try {
                    storageService.delete(asset.getStorageKey());
                    filesDeleted++;
                } catch (RuntimeException e) {
                    log.warn("audio file cleanup failed: key={} {}", asset.getStorageKey(), e.getClass().getSimpleName());
                }
                asset.setStatus(AudioAsset.STATUS_SUPERSEDED);
                asset.setStorageKey(null);
                asset.setPublicUrl(null);
                audioAssetRepository.save(asset);
            }
        }
        if (!expired.isEmpty() || filesDeleted > 0) {
            log.info("audio cleanup done: expiredStories={} filesDeleted={}", expired.size(), filesDeleted);
        }
    }
}
