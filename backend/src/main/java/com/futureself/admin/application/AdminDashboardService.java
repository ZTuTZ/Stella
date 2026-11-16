package com.futureself.admin.application;

import com.futureself.ai.domain.AiCallLogRepository;
import com.futureself.audio.domain.AudioAsset;
import com.futureself.audio.domain.AudioAssetRepository;
import com.futureself.generation.domain.GenerationTask;
import com.futureself.generation.domain.GenerationTaskRepository;
import com.futureself.goal.domain.GoalRepository;
import com.futureself.story.domain.StoryRepository;
import com.futureself.user.domain.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dashboard 汇总（§20.1）：注册/活跃/新增目标/生成数/音频成败/失败任务/平均耗时。
 * 均由现有表即时计算；V1 不引入额外统计表。
 */
@Service
public class AdminDashboardService {

    private final AppUserRepository userRepository;
    private final GoalRepository goalRepository;
    private final StoryRepository storyRepository;
    private final AudioAssetRepository audioAssetRepository;
    private final GenerationTaskRepository taskRepository;
    private final AiCallLogRepository aiCallLogRepository;

    public AdminDashboardService(AppUserRepository userRepository,
                                 GoalRepository goalRepository,
                                 StoryRepository storyRepository,
                                 AudioAssetRepository audioAssetRepository,
                                 GenerationTaskRepository taskRepository,
                                 AiCallLogRepository aiCallLogRepository) {
        this.userRepository = userRepository;
        this.goalRepository = goalRepository;
        this.storyRepository = storyRepository;
        this.audioAssetRepository = audioAssetRepository;
        this.taskRepository = taskRepository;
        this.aiCallLogRepository = aiCallLogRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalUsers", userRepository.count());
        m.put("todayActiveUsers", userRepository.countByLastActiveAtAfter(dayStart));
        m.put("todayNewGoals", goalRepository.countByCreatedAtBetween(dayStart, dayEnd));
        m.put("todayNewStories", storyRepository.countByCreatedAtBetween(dayStart, dayEnd));
        m.put("audioReadyTotal", audioAssetRepository.countByStatus(AudioAsset.STATUS_READY));
        m.put("audioFailedTotal", audioAssetRepository.countByStatus(AudioAsset.STATUS_FAILED));
        m.put("audioReadyToday", audioAssetRepository.countByStatusAndCreatedAtBetween(AudioAsset.STATUS_READY, dayStart, dayEnd));
        m.put("audioFailedToday", audioAssetRepository.countByStatusAndCreatedAtBetween(AudioAsset.STATUS_FAILED, dayStart, dayEnd));
        m.put("failedTasks", taskRepository.countByStatus(GenerationTask.STATUS_FAILED));
        m.put("textAiAvgLatencyMs", avgOrNull(aiCallLogRepository.avgSuccessLatency("TEXT")));
        m.put("audioAiAvgLatencyMs", avgOrNull(aiCallLogRepository.avgSuccessLatency("AUDIO")));
        return m;
    }

    private Long avgOrNull(Double avg) {
        return avg == null ? null : Math.round(avg);
    }
}
