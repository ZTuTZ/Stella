package com.futureself.audio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AudioAssetRepository extends JpaRepository<AudioAsset, Long> {

    /** 当前有效音频：非 SUPERSEDED 的最新一条。 */
    Optional<AudioAsset> findTopByStoryIdAndStatusNotOrderByIdDesc(Long storyId, String status);
}
