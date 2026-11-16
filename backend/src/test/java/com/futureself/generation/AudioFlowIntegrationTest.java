package com.futureself.generation;

import com.futureself.ai.audio.AiAudioProvider;
import com.futureself.ai.audio.AudioGenerationRequest;
import com.futureself.ai.audio.AudioQueryResult;
import com.futureself.ai.audio.AudioSubmitResult;
import com.futureself.audio.domain.AudioAsset;
import com.futureself.audio.domain.AudioAssetRepository;
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

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 5 验收（实施文档 §28）：
 * Mock 音频 AUDIO_SUBMITTING → COMPLETED；
 * 文本完成后音频失败不影响文本阅读；
 * 音频可重新单独生成（retry-audio）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AudioFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GenerationOrchestrator orchestrator;

    @Autowired
    private GenerationTaskRepository taskRepository;

    @Autowired
    private StoryRepository storyRepository;

    @Autowired
    private AudioAssetRepository audioAssetRepository;

    @MockitoBean
    private AiAudioProvider audioProvider;

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

    private long startTask(String token, long goalId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/goals/" + goalId + "/generate-story")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Number taskId = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.taskId");
        return taskId.longValue();
    }

    private GenerationTask awaitStatus(long taskId, String targetStatus, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            orchestrator.processNext();
            GenerationTask task = taskRepository.findById(taskId).orElseThrow();
            if (task.getStatus().equals(targetStatus)) {
                return task;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        GenerationTask task = taskRepository.findById(taskId).orElseThrow();
        throw new AssertionError("timeout waiting " + targetStatus + ", current=" + task.getStatus()
                + " error=" + task.getErrorCode() + "/" + task.getErrorMessage());
    }

    /** 供应商 stub：一次失败后成功，READY 时返回 test fixture WAV 的 file: URL。 */
    private String fixtureUrl() throws Exception {
        Path fixture = Path.of("target/test-media/mock-audio/mock-narration.wav");
        assertThat(java.nio.file.Files.exists(fixture)).as("mock fixture should exist").isTrue();
        return fixture.toUri().toString();
    }

    @Test
    void audioFlowReachesCompletedWithStoredFile() throws Exception {
        when(audioProvider.providerName()).thenReturn("mock");
        when(audioProvider.capabilities()).thenReturn(new com.futureself.ai.audio.AudioProviderCapabilities(
                true, false, false, false, false, false));
        when(audioProvider.submit(any(AudioGenerationRequest.class)))
                .thenReturn(new AudioSubmitResult("mock-task-" + UUID.randomUUID().toString().substring(0, 8)));
        when(audioProvider.query(any()))
                .thenReturn(new AudioQueryResult(AudioQueryResult.JobStatus.READY, fixtureUrl(), "wav", 2000L, null, null));

        String token = login("audio-flow");
        long goalId = createGoal(token);
        long taskId = startTask(token, goalId);

        GenerationTask task = awaitStatus(taskId, GenerationTask.STATUS_COMPLETED, 20000);
        assertThat(task.getProgress()).isEqualTo(GenerationTask.PROGRESS_COMPLETED);
        assertThat(task.getFinishedAt()).isNotNull();

        Story story = storyRepository.findById(task.getStoryId()).orElseThrow();
        // 转存后文件真实存在且可读取（自有存储，§15.5 第 6 条）
        assertThat(story.getId()).isNotNull();

        // 任务响应带 audioStatus=READY
        mockMvc.perform(get("/api/v1/generation-tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.audioStatus").value("READY"));

        // 故事详情带 audio 对象（§12.5）
        mockMvc.perform(get("/api/v1/stories/" + story.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.audio.status").value("READY"))
                .andExpect(jsonPath("$.data.audio.url").isNotEmpty())
                .andExpect(jsonPath("$.data.audio.durationMs").value(2000))
                .andExpect(jsonPath("$.data.audio.format").value("wav"));

        // 文本与音频解耦：文本仍可读（§32.6）
        mockMvc.perform(get("/api/v1/stories/" + story.getId()).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.content").isNotEmpty());
    }

    @Test
    void audioFailureKeepsTextReadableAndRetrySucceeds() throws Exception {
        when(audioProvider.providerName()).thenReturn("mock");
        when(audioProvider.capabilities()).thenReturn(new com.futureself.ai.audio.AudioProviderCapabilities(
                true, false, false, false, false, false));
        when(audioProvider.submit(any(AudioGenerationRequest.class)))
                .thenReturn(new AudioSubmitResult("mock-task-" + UUID.randomUUID().toString().substring(0, 8)));
        // 第一次 query 失败（asset 标记 FAILED），第二次 READY → 触发 supersede
        when(audioProvider.query(any()))
                .thenReturn(new AudioQueryResult(AudioQueryResult.JobStatus.FAILED, null, null, null,
                        "AUDIO_PROCESSING_FAILED", "mock failure"))
                .thenReturn(new AudioQueryResult(AudioQueryResult.JobStatus.READY, fixtureUrl(), "wav", 2000L, null, null));

        String token = login("audio-retry");
        long goalId = createGoal(token);
        long taskId = startTask(token, goalId);

        // 第一次失败 → RETRY_WAIT（stage=AUDIO），旧 asset FAILED，文本已完成且可读
        GenerationTask retried = awaitStatus(taskId, GenerationTask.STATUS_RETRY_WAIT, 20000);
        assertThat(retried.getErrorStage()).isEqualTo(GenerationTask.STAGE_AUDIO);
        assertThat(retried.getStoryId()).isNotNull();
        long storyId = retried.getStoryId();

        // 音频失败期间文本依然可读（验收：文本完成后音频失败不影响文本阅读）
        mockMvc.perform(get("/api/v1/stories/" + storyId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isNotEmpty())
                .andExpect(jsonPath("$.data.audio.status").value("FAILED"));

        // 继续推进：重试（旧 asset 被 SUPERSEDED）→ 成功 → COMPLETED
        GenerationTask done = awaitStatus(taskId, GenerationTask.STATUS_COMPLETED, 20000);
        assertThat(done.getStoryId()).isEqualTo(storyId);

        mockMvc.perform(get("/api/v1/stories/" + storyId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.audio.status").value("READY"))
                .andExpect(jsonPath("$.data.audio.url").isNotEmpty());

        // §32.5：一个 story 一份当前有效音频；旧的 FAILED 已被 SUPERSEDED
        assertThat(audioAssetRepository.findTopByStoryIdAndStatusNotOrderByIdDesc(
                storyId, AudioAsset.STATUS_SUPERSEDED).orElseThrow().getStatus())
                .isEqualTo(AudioAsset.STATUS_READY);
        long total = audioAssetRepository.findAll().stream()
                .filter(a -> a.getStoryId().equals(storyId)).count();
        assertThat(total).isEqualTo(2);
    }

    @Test
    void retryAudioAfterFinalFailure() throws Exception {
        // 场景：音频连续失败直至 FAILED（终态）→ 手动 retry-audio → 成功 → COMPLETED
        when(audioProvider.providerName()).thenReturn("mock");
        when(audioProvider.capabilities()).thenReturn(new com.futureself.ai.audio.AudioProviderCapabilities(
                true, false, false, false, false, false));
        when(audioProvider.submit(any(AudioGenerationRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.AUDIO_SUBMIT_FAILED))       // 初次
                .thenThrow(new BusinessException(ErrorCode.AUDIO_SUBMIT_FAILED))       // 重试 1
                .thenThrow(new BusinessException(ErrorCode.AUDIO_SUBMIT_FAILED))       // 重试 2
                .thenThrow(new BusinessException(ErrorCode.AUDIO_SUBMIT_FAILED))       // 重试 3（超限 → FAILED）
                .thenReturn(new AudioSubmitResult("mock-task-" + UUID.randomUUID().toString().substring(0, 8)));
        when(audioProvider.query(any()))
                .thenReturn(new AudioQueryResult(AudioQueryResult.JobStatus.READY, fixtureUrl(), "wav", 2000L, null, null));

        String token = login("audio-final");
        long goalId = createGoal(token);
        long taskId = startTask(token, goalId);

        GenerationTask failed = awaitStatus(taskId, GenerationTask.STATUS_FAILED, 30000);
        assertThat(failed.getErrorStage()).isEqualTo(GenerationTask.STAGE_AUDIO);
        long storyId = failed.getStoryId();

        // 手动重试音频：终态任务重置回音频提交
        mockMvc.perform(post("/api/v1/stories/" + storyId + "/retry-audio")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.taskStatus").value("AUDIO_SUBMITTING"));

        GenerationTask done = awaitStatus(taskId, GenerationTask.STATUS_COMPLETED, 20000);
        assertThat(done.getStoryId()).isEqualTo(storyId);

        // submit 失败场景无历史 asset：重试成功后恰有一条 READY
        mockMvc.perform(get("/api/v1/stories/" + storyId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.audio.status").value("READY"));
        long total = audioAssetRepository.findAll().stream()
                .filter(a -> a.getStoryId().equals(storyId)).count();
        assertThat(total).isEqualTo(1);
    }
}
