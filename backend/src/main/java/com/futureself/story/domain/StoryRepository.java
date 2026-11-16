package com.futureself.story.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StoryRepository extends JpaRepository<Story, Long> {

    /** 用户自己的、审核通过且未删除的故事（详情入口）。 */
    Optional<Story> findByIdAndUserIdAndStatus(Long id, Long userId, String status);

    // —— Library / Home（Phase 7）——

    Page<Story> findByUserIdAndStatusAndModerationStatus(Long userId, String status, String moderationStatus, Pageable pageable);

    Page<Story> findByUserIdAndStatusAndModerationStatusAndFavorite(
            Long userId, String status, String moderationStatus, boolean favorite, Pageable pageable);

    /** 最近 N 条（首页）。 */
    List<Story> findTop3ByUserIdAndStatusAndModerationStatusOrderByCreatedAtDesc(
            Long userId, String status, String moderationStatus);

    /** 最近一条（首页主卡）。 */
    Optional<Story> findTopByUserIdAndStatusAndModerationStatusOrderByCreatedAtDesc(
            Long userId, String status, String moderationStatus);

    /** 今日最近一条（首页主卡优先）。 */
    Optional<Story> findTopByUserIdAndStatusAndModerationStatusAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId, String status, String moderationStatus, LocalDateTime start, LocalDateTime end);
}
