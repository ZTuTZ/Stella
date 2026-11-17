package com.futureself.audio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AudioAssetRepository extends JpaRepository<AudioAsset, Long> {

    /** 当前有效音频：非 SUPERSEDED 的最新一条。 */
    Optional<AudioAsset> findTopByStoryIdAndStatusNotOrderByIdDesc(Long storyId, String status);

    /** 最新 READY 音频（H-05：blue-green 期间优先返回可播放的旧音频）。 */
    Optional<AudioAsset> findTopByStoryIdAndStatusOrderByIdDesc(Long storyId, String status);

    /** 清理 SUPERSEDED 且超过保留期的对象（H-05 §6.4）。 */
    List<AudioAsset> findByStatusAndUpdatedAtBefore(String status, LocalDateTime cutoff);

    List<AudioAsset> findByStoryId(Long storyId);

    long countByStatus(String status);

    long countByStatusAndCreatedAtBetween(String status, LocalDateTime start, LocalDateTime end);
}
