package com.futureself.admin.application;

import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.generation.domain.GenerationTask;
import com.futureself.generation.domain.GenerationTaskRepository;
import com.futureself.goal.domain.GoalRepository;
import com.futureself.goal.domain.ManifestationGoal;
import com.futureself.profile.domain.LifeProfile;
import com.futureself.profile.domain.LifeProfileRepository;
import com.futureself.story.domain.Story;
import com.futureself.story.domain.StoryRepository;
import com.futureself.user.domain.AppUser;
import com.futureself.user.domain.AppUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理后台用户服务（§20.2）：列表/详情（档案/目标/故事/任务）/封禁。
 * openid 在列表与详情中脱敏展示；Life Profile 展示时标注仅供内测排查。
 */
@Service
public class AdminUserService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    private final AppUserRepository userRepository;
    private final LifeProfileRepository profileRepository;
    private final GoalRepository goalRepository;
    private final StoryRepository storyRepository;
    private final GenerationTaskRepository taskRepository;

    public AdminUserService(AppUserRepository userRepository,
                            LifeProfileRepository profileRepository,
                            GoalRepository goalRepository,
                            StoryRepository storyRepository,
                            GenerationTaskRepository taskRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.goalRepository = goalRepository;
        this.storyRepository = storyRepository;
        this.taskRepository = taskRepository;
    }

    public record UserBrief(long id, String displayName, String openidMasked, String role, String status,
                            boolean onboardingCompleted, String lastActiveAt, String createdAt) {
    }

    public Page<UserBrief> pageUsers(String keyword, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.desc("createdAt")));
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return userRepository.adminPage(kw, pageable).map(this::toBrief);
    }

    /** openid 脱敏：仅内测排查使用，不完整展示。 */
    public static String maskOpenid(String openid) {
        if (openid == null || openid.length() <= 6) {
            return "***";
        }
        return openid.substring(0, 6) + "***";
    }

    private UserBrief toBrief(AppUser user) {
        return new UserBrief(
                user.getId(),
                user.getDisplayName(),
                maskOpenid(user.getOpenid()),
                user.getRole(),
                user.getStatus(),
                user.isOnboardingCompleted(),
                user.getLastActiveAt() == null ? null : user.getLastActiveAt().toString(),
                user.getCreatedAt() == null ? null : user.getCreatedAt().toString());
    }

    public Map<String, Object> detail(long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND, "user not found"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", toBrief(user));

        // Life Profile（仅供内测排查，界面需标注用途）
        result.put("lifeProfile", profileRepository.findByUserId(userId)
                .map(this::profileToMap).orElse(null));

        result.put("goals", goalRepository.adminPage(userId, null, PageRequest.of(0, 50))
                .getContent().stream().map(this::goalToMap).toList());
        result.put("stories", storyRepository.adminPage(userId, null, PageRequest.of(0, 50))
                .getContent().stream().map(this::storyToMap).toList());
        result.put("recentTasks", taskRepository.adminPage(userId, null, PageRequest.of(0, 20))
                .getContent().stream().map(this::taskToMap).toList());
        return result;
    }

    private Map<String, Object> profileToMap(LifeProfile profile) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profileVersion", profile.getProfileVersion());
        m.put("currentSituation", profile.getCurrentSituation());
        m.put("focusAreasJson", profile.getFocusAreasJson());
        m.put("importantPeopleJson", profile.getImportantPeopleJson());
        m.put("preferencesJson", profile.getPreferencesJson());
        return m;
    }

    private Map<String, Object> goalToMap(ManifestationGoal goal) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", goal.getId());
        m.put("category", goal.getCategory());
        m.put("title", goal.getTitle());
        m.put("status", goal.getStatus());
        m.put("createdAt", goal.getCreatedAt() == null ? null : goal.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> storyToMap(Story story) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", story.getId());
        m.put("title", story.getTitle());
        m.put("status", story.getStatus());
        m.put("moderationStatus", story.getModerationStatus());
        m.put("provider", story.getProvider());
        m.put("model", story.getModel());
        m.put("createdAt", story.getCreatedAt() == null ? null : story.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> taskToMap(GenerationTask task) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", task.getId());
        m.put("status", task.getStatus());
        m.put("progress", task.getProgress());
        m.put("storyId", task.getStoryId());
        m.put("retryCount", task.getRetryCount());
        m.put("errorStage", task.getErrorStage());
        m.put("errorCode", task.getErrorCode());
        m.put("errorMessage", task.getErrorMessage());
        m.put("createdAt", task.getCreatedAt() == null ? null : task.getCreatedAt().toString());
        return m;
    }

    /** 封禁/解封（§3.2）：DISABLED 用户的所有 token 立即失效（鉴权层校验状态）。 */
    @Transactional
    public Map<String, Object> updateStatus(long userId, String status) {
        if (!STATUS_ACTIVE.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "status must be ACTIVE or DISABLED");
        }
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND, "user not found"));
        user.setStatus(status);
        userRepository.save(user);
        return Map.of("id", user.getId(), "status", user.getStatus());
    }
}
