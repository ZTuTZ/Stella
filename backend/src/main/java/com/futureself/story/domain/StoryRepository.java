package com.futureself.story.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // —— Admin / Cleanup（Phase 8）——

    /** 后台分页：userId/status 可空（含 BLOCKED/DELETED）。 */
    @Query("select s from Story s where (:userId is null or s.userId = :userId) and (:status is null or s.status = :status)")
    Page<Story> adminPage(@Param("userId") Long userId, @Param("status") String status, Pageable pageable);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /** 已删除故事中超过保留期的（音频物理清理 §25.2）。 */
    List<Story> findByStatusAndUpdatedAtBefore(String status, LocalDateTime time);

    /** 注销清理（§25.3）：用户全部 ACTIVE 故事。 */
    List<Story> findByUserIdAndStatus(Long userId, String status);
}
