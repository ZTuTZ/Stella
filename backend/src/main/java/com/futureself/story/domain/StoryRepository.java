package com.futureself.story.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoryRepository extends JpaRepository<Story, Long> {

    /** 用户自己的、审核通过且未删除的故事（详情入口）。 */
    Optional<Story> findByIdAndUserIdAndStatus(Long id, Long userId, String status);
}
