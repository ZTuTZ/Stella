package com.futureself.generation;

import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.generation.domain.GenerationTask;
import com.futureself.generation.domain.GenerationTaskRepository;
import com.futureself.story.domain.Story;
import com.futureself.story.domain.StoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 验收（实施文档 §28）：
 * PENDING → TEXT_READY；文本 AI 失败可重试；重启可恢复（状态残留续跑）；
 * 输入/输出 moderation 拦截；Idempotency-Key 幂等 + 运行中防重。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GenerationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GenerationOrchestrator orchestrator;

    @Autowired
    private GenerationTaskRepository taskRepository;

    @Autowired
    private StoryRepository storyRepository;

    @MockitoBean
    private com.futureself.ai.text.TextAiProvider textAiProvider;

    private String login(String base) throws Exception {
        String code = base + "-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/wechat/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private long createGoal(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/goals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goalCategory":"事业","goalTitle":"拥有一间自己的设计工作室",
                                 "goalDescription":"一年后开一间小而稳的设计工作室，收入更稳定，有更多自由时间。"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private long startTask(String token, long goalId, String idempotencyKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/goals/" + goalId + "/generate-story")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        Number taskId = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.taskId");
        return taskId.longValue();
    }

    /** 手动驱动编排器直至任务达到目标状态或超时。 */
    private GenerationTask awaitStatus(long taskId, String targetStatus, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            orchestrator.processNext();
            GenerationTask task = taskRepository.findById(taskId).orElseThrow();
            if (task.getStatus().equals(targetStatus)) {
                return task;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        GenerationTask task = taskRepository.findById(taskId).orElseThrow();
        throw new AssertionError("timeout waiting " + targetStatus + ", current=" + task.getStatus()
                + " error=" + task.getErrorCode() + "/" + task.getErrorMessage());
    }

    @Test
    void fullTextFlowReachesTextReady() throws Exception {
        when(textAiProvider.providerName()).thenReturn("mock");
        when(textAiProvider.generateStructured(any(), eq(StoryGenerationResult.class)))
                .thenReturn(new StoryGenerationResult(
                        "工作室月入稳定后的一个普通早晨",
                        "你从自己的家里醒来，开始一段已经稳定下来的生活。",
                        "清晨的光落在木质书桌上。你像往常一样醒来，工作室的门牌挂在楼下……",
                        new StoryGenerationResult.AudioDirection("warm_calm", "slow", "自然、亲密")));

        String token = login("gen-flow");
        long goalId = createGoal(token);
        long taskId = startTask(token, goalId, "idem-" + UUID.randomUUID());

        GenerationTask task = awaitStatus(taskId, GenerationTask.STATUS_TEXT_READY, 15000);
        assertThat(task.getProgress()).isEqualTo(GenerationTask.PROGRESS_TEXT_READY);
        assertThat(task.getStoryId()).isNotNull();
        assertThat(task.getRetryCount()).isZero();

        // 故事关联字段（§7.1/§28 验收）：profile_version / prompt_template_id / provider / model
        Story story = storyRepository.findById(task.getStoryId()).orElseThrow();
        assertThat(story.getPromptTemplateId()).isNotNull();
        assertThat(story.getProvider()).isEqualTo("mock");
        assertThat(story.getProfileVersion()).isPositive();
        assertThat(story.getModerationStatus()).isEqualTo(Story.MODERATION_PASSED);

        // 用户可读
        mockMvc.perform(get("/api/v1/stories/" + story.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("工作室月入稳定后的一个普通早晨"))
                .andExpect(jsonPath("$.data.aiGenerated").value(true))
                .andExpect(jsonPath("$.data.content").isNotEmpty())
                .andExpect(jsonPath("$.data.favorite").value(false));

        // 他人不可见
        String tokenB = login("gen-flow-other");
        mockMvc.perform(get("/api/v1/stories/" + story.getId()).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void textAiFailureRetriesThenSucceeds() throws Exception {
        when(textAiProvider.providerName()).thenReturn("mock");
        when(textAiProvider.generateStructured(any(), eq(StoryGenerationResult.class)))
                .thenThrow(new BusinessException(ErrorCode.TEXT_AI_UNAVAILABLE))
                .thenReturn(new StoryGenerationResult(
                        "重试后的故事", "摘要", "正文内容……", null));

        String token = login("gen-retry");
        long goalId = createGoal(token);
        long taskId = startTask(token, goalId, "idem-" + UUID.randomUUID());

        // 第一次推进：PENDING → TEXT_GENERATING → AI 失败 → RETRY_WAIT（test 配置退避 0，可立即重试）
        GenerationTask afterFirst = awaitStatus(taskId, GenerationTask.STATUS_RETRY_WAIT, 10000);
        assertThat(afterFirst.getRetryCount()).isEqualTo(1);
        assertThat(afterFirst.getErrorStage()).isEqualTo(GenerationTask.STAGE_TEXT);
        assertThat(afterFirst.getErrorCode()).isEqualTo("TEXT_AI_UNAVAILABLE");

        // 第二次推进：RETRY_WAIT → TEXT_GENERATING → 成功 → TEXT_MODERATING → TEXT_READY
        GenerationTask done = awaitStatus(taskId, GenerationTask.STATUS_TEXT_READY, 15000);
        assertThat(done.getRetryCount()).isEqualTo(1);
        assertThat(done.getStoryId()).isNotNull();
    }

    @Test
    void taskResumesFromInterruptedStage() throws Exception {
        // 模拟“生成中途崩溃”：任务停留在 TEXT_GENERATING 且 locked
        when(textAiProvider.providerName()).thenReturn("mock");
        when(textAiProvider.generateStructured(any(), eq(StoryGenerationResult.class)))
                .thenReturn(new StoryGenerationResult("恢复后的故事", "摘要", "正文……", null));

        String token = login("gen-resume");
        long goalId = createGoal(token);
        long taskId = startTask(token, goalId, "idem-" + UUID.randomUUID());

        GenerationTask task = taskRepository.findById(taskId).orElseThrow();
        task.setStatus(GenerationTask.STATUS_TEXT_GENERATING);
        task.setProgress(GenerationTask.PROGRESS_TEXT_GENERATING);
        task.setLockedAt(LocalDateTime.now().minusMinutes(1));
        task.setWorkerId("dead-worker");
        taskRepository.save(task);

        GenerationTask resumed = awaitStatus(taskId, GenerationTask.STATUS_TEXT_READY, 15000);
        assertThat(resumed.getStoryId()).isNotNull();
    }

    @Test
    void blockedGoalInputRejected() throws Exception {
        String token = login("gen-blocked-input");
        mockMvc.perform(post("/api/v1/goals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goalCategory":"爱情","goalTitle":"让他后悔",
                                 "goalDescription":"我会一直跟踪他，让他后悔离开我，重新回到我身边。"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("GOAL_CONTENT_BLOCKED"));
    }

    @Test
    void blockedStoryOutputBlocksTask() throws Exception {
        when(textAiProvider.providerName()).thenReturn("mock");
        when(textAiProvider.generateStructured(any(), eq(StoryGenerationResult.class)))
                .thenReturn(new StoryGenerationResult(
                        "异常故事", "摘要", "故事里出现了自杀情节……", null));

        String token = login("gen-blocked-output");
        long goalId = createGoal(token);
        long taskId = startTask(token, goalId, "idem-" + UUID.randomUUID());

        GenerationTask blocked = awaitStatus(taskId, GenerationTask.STATUS_BLOCKED, 15000);
        assertThat(blocked.getErrorStage()).isEqualTo(GenerationTask.STAGE_MODERATION);
        assertThat(blocked.getErrorCode()).isEqualTo("MODERATION_BLOCKED");
        assertThat(blocked.getFinishedAt()).isNotNull();

        Story story = storyRepository.findById(blocked.getStoryId()).orElseThrow();
        assertThat(story.getStatus()).isEqualTo(Story.STATUS_BLOCKED);
        assertThat(story.getModerationStatus()).isEqualTo(Story.MODERATION_BLOCKED);

        // BLOCKED 故事对用户不可见
        mockMvc.perform(get("/api/v1/stories/" + story.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void idempotencyAndRunningCheck() throws Exception {
        when(textAiProvider.providerName()).thenReturn("mock");
        when(textAiProvider.generateStructured(any(), eq(StoryGenerationResult.class)))
                .thenReturn(new StoryGenerationResult("幂等故事", "摘要", "正文……", null));

        String token = login("gen-idem");
        long goalId = createGoal(token);
        String key = "idem-key-" + UUID.randomUUID();

        long taskId1 = startTask(token, goalId, key);
        long taskId2 = startTask(token, goalId, key); // 同 key 幂等 → 同一任务
        assertThat(taskId2).isEqualTo(taskId1);

        // 不同 key 且任务未完成 → TASK_ALREADY_RUNNING
        mockMvc.perform(post("/api/v1/goals/" + goalId + "/generate-story")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "other-" + UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_ALREADY_RUNNING"));

        // 任务到 TEXT_READY（非终态，仍运行中）→ 依然 409
        awaitStatus(taskId1, GenerationTask.STATUS_TEXT_READY, 15000);
        mockMvc.perform(post("/api/v1/goals/" + goalId + "/generate-story")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "other-" + UUID.randomUUID()))
                .andExpect(status().isConflict());

        // 他人任务状态不可见
        String tokenB = login("gen-idem-other");
        mockMvc.perform(get("/api/v1/generation-tasks/" + taskId1).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }
}
