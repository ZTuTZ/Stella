package com.futureself.goal.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoalRepository extends JpaRepository<ManifestationGoal, Long> {

    /** 用户自己的未删除目标，按创建时间倒序（§10.3 索引 idx_goal_user_status）。 */
    List<ManifestationGoal> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
}
