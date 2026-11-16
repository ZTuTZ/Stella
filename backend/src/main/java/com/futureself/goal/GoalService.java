package com.futureself.goal;

import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.goal.api.CreateGoalRequest;
import com.futureself.goal.api.GoalResponse;
import com.futureself.goal.api.UpdateGoalRequest;
import com.futureself.goal.domain.GoalRepository;
import com.futureself.goal.domain.ManifestationGoal;
import com.futureself.moderation.ModerationProvider;
import com.futureself.moderation.ModerationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Goal CRUD（实施文档 §9.3/§12.3）。
 * 所有按 id 的访问都再次校验 owner（§3.1）；删除为软删除（§32.8）；
 * 输入审核（§19.2 阶段 1）：Goal 输入后检查，拦截 → GOAL_CONTENT_BLOCKED。
 */
@Service
public class GoalService {

    /** §5.2 目标分类（与关注领域一致）。 */
    public static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "事业", "财富", "爱情", "家庭", "自信", "健康生活方式", "学习成长", "旅行", "其他");

    private final GoalRepository goalRepository;
    private final ModerationProvider moderationProvider;

    public GoalService(GoalRepository goalRepository, ModerationProvider moderationProvider) {
        this.goalRepository = goalRepository;
        this.moderationProvider = moderationProvider;
    }

    private void moderateGoalInput(long userId, String title, String description) {
        var result = moderationProvider.check(
                new ModerationRequest("GOAL_INPUT", userId, title + "\n" + description));
        if (result.blocked()) {
            throw new BusinessException(ErrorCode.GOAL_CONTENT_BLOCKED);
        }
    }

    @Transactional
    public GoalResponse create(long userId, CreateGoalRequest request) {
        if (!ALLOWED_CATEGORIES.contains(request.goalCategory())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "unknown goalCategory");
        }
        moderateGoalInput(userId, request.goalTitle(), request.goalDescription());
        ManifestationGoal goal = new ManifestationGoal();
        goal.setUserId(userId);
        goal.setCategory(request.goalCategory());
        goal.setTitle(request.goalTitle());
        goal.setDescription(request.goalDescription());
        goal.setTargetTimeText(request.targetTimeText());
        goal.setStatus(ManifestationGoal.STATUS_ACTIVE);
        ManifestationGoal saved = goalRepository.save(goal);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> listOwn(long userId) {
        return goalRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(userId, ManifestationGoal.STATUS_ACTIVE)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ManifestationGoal loadOwnGoal(long goalId, long userId) {
        return goalRepository.findById(goalId)
                .filter(goal -> goal.getUserId().equals(userId))
                .filter(goal -> !ManifestationGoal.STATUS_DELETED.equals(goal.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.GOAL_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public GoalResponse getOwn(long goalId, long userId) {
        return toResponse(loadOwnGoal(goalId, userId));
    }

    @Transactional
    public GoalResponse updateOwn(long goalId, long userId, UpdateGoalRequest request) {
        ManifestationGoal goal = loadOwnGoal(goalId, userId);
        if (request.goalCategory() != null) {
            if (!ALLOWED_CATEGORIES.contains(request.goalCategory())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "unknown goalCategory");
            }
            goal.setCategory(request.goalCategory());
        }
        boolean textChanged = false;
        if (request.goalTitle() != null) {
            goal.setTitle(request.goalTitle());
            textChanged = true;
        }
        if (request.goalDescription() != null) {
            goal.setDescription(request.goalDescription());
            textChanged = true;
        }
        if (request.targetTimeText() != null) {
            goal.setTargetTimeText(request.targetTimeText());
        }
        if (textChanged) {
            moderateGoalInput(userId, goal.getTitle(), goal.getDescription());
        }
        return toResponse(goalRepository.save(goal));
    }

    @Transactional
    public void deleteOwn(long goalId, long userId) {
        ManifestationGoal goal = loadOwnGoal(goalId, userId);
        goal.setStatus(ManifestationGoal.STATUS_DELETED);
        goalRepository.save(goal);
    }

    public GoalResponse toResponse(ManifestationGoal goal) {
        Map<String, Object> details = JsonSupport.parseObject(goal.getDetailsJson());
        return new GoalResponse(
                goal.getId(),
                goal.getCategory(),
                goal.getTitle(),
                goal.getDescription(),
                goal.getTargetTimeText(),
                details,
                goal.getStatus(),
                goal.getCreatedAt() == null ? null : goal.getCreatedAt().toString());
    }
}
