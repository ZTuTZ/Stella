package com.futureself.user;

import com.futureself.goal.domain.GoalRepository;
import com.futureself.goal.domain.ManifestationGoal;
import com.futureself.story.domain.Story;
import com.futureself.story.domain.StoryRepository;
import com.futureself.user.domain.AppUser;
import com.futureself.user.domain.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户注销（实施文档 §25.3）：V1 预留 service，UI 暂不开放，但数据模型可完整清理。
 * requestDeletion：禁用账号 + 昵称脱敏 + 软删除全部目标/故事；
 * 音频文件的物理删除由 AudioCleanupJob 按保留期处理（§25.2）。
 * 不做物理删除（保留审计能力）；彻底清除由运维工具按合规要求另行处理。
 */
@Service
public class UserDeletionService {

    private static final Logger log = LoggerFactory.getLogger(UserDeletionService.class);

    private final AppUserRepository userRepository;
    private final GoalRepository goalRepository;
    private final StoryRepository storyRepository;

    public UserDeletionService(AppUserRepository userRepository,
                               GoalRepository goalRepository,
                               StoryRepository storyRepository) {
        this.userRepository = userRepository;
        this.goalRepository = goalRepository;
        this.storyRepository = storyRepository;
    }

    @Transactional
    public Map<String, Object> requestDeletion(long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));

        user.setStatus(UserService.STATUS_DISABLED);
        user.setDisplayName("已注销用户");
        userRepository.save(user);

        List<ManifestationGoal> activeGoals = goalRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(userId, ManifestationGoal.STATUS_ACTIVE);
        activeGoals.forEach(g -> g.setStatus(ManifestationGoal.STATUS_DELETED));
        goalRepository.saveAll(activeGoals);

        List<Story> activeStories = storyRepository.findByUserIdAndStatus(userId, Story.STATUS_ACTIVE);
        activeStories.forEach(s -> s.setStatus(Story.STATUS_DELETED));
        storyRepository.saveAll(activeStories);

        log.info("user deletion requested: userId={} goals={} stories={}",
                userId, activeGoals.size(), activeStories.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("status", UserService.STATUS_DISABLED);
        result.put("goalsSoftDeleted", activeGoals.size());
        result.put("storiesSoftDeleted", activeStories.size());
        return result;
    }
}
