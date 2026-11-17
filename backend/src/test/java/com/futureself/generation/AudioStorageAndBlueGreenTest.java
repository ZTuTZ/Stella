package com.futureself.generation;

import com.futureself.ai.audio.AiAudioProvider;
import com.futureself.ai.audio.AudioQueryResult;
import com.futureself.ai.audio.AudioSubmitResult;
import com.futureself.audio.domain.AudioAsset;
import com.futureself.audio.domain.AudioAssetRepository;
import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.generation.domain.GenerationTask;
import com.futureself.generation.domain.GenerationTaskRepository;
import com.futureself.storage.StoredObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H-04 + H-05：
 * 1) Storage 偶发失败只重试转存，TTS 只提交一次；
 * 2) Storage 重试耗尽 → task FAILED + asset FAILED；
 * 3) 重生成期间旧 READY 音频仍可播放，新音频 READY 后原子切换。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AudioStorageAndBlueGreenTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GenerationOrchestrator orchestrator;

    @Autowired
    private GenerationTaskRepository taskRepository;

    @Autowired
    private AudioAssetRepository audioAssetRepository;

    @MockitoBean
    private AiAudioProvider audioProvider;

    @MockitoBean
    private AudioTransferService audioTransferService;

    @BeforeEach
    void reset() {
        taskRepository.deleteByStatusIn(List.of(
                GenerationTask.STATUS_PENDING,
                GenerationTask.STATUS_RETRY_WAIT,
                GenerationTask.STATUS_TEXT_GENERATING,
                GenerationTask.STATUS_TEXT_MODERATING,
                GenerationTask.STATUS_TEXT_READY,
                GenerationTask.STATUS_AUDIO_SUBMITTING,
                GenerationTask.STATUS_AUDIO_PROCESSING,
                GenerationTask.STATUS_AUDIO_STORING));
        when(audioProvider.providerName()).thenReturn("fake-tts");
    }

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
                                {"goalCategory":"事业","goalTitle":"拥有自己的工作室",
                                 "goalDescription":"一年后开一间小而稳的设计工作室。"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private long startTask(String token, long goalId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/goals/" + goalId + "/generate-story")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "audio-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.taskId")).longValue();
    }

    private GenerationTask driveUntil(long taskId, String targetStatus, int maxIterations) {
        for (int i = 0; i < maxIterations; i++) {
            orchestrator.processNext();
            GenerationTask task = taskRepository.findById(taskId).orElseThrow();
            if (targetStatus.equals(task.getStatus())) {
                return task;
            }
        }
        GenerationTask task = taskRepository.findById(taskId).orElseThrow();
        throw new AssertionError("timeout waiting " + targetStatus + ", current=" + task.getStatus());
    }

    private String storyAudioUrl(String token, long storyId) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/stories/" + storyId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(r.getResponse().getContentAsString(), "$.data.audio.url");
    }

    @Test
    void storageRetryDoesNotResubmitTts() throws Exception {
        String token = login("audio-store-retry");
        long goalId = createGoal(token);
        long taskId = startTask(token, goalId);

        when(audioProvider.submit(any())).thenReturn(new AudioSubmitResult("prov-1"));
        when(audioProvider.query("prov-1")).thenReturn(new AudioQueryResult(
                AudioQueryResult.JobStatus.READY, "https://provider.example/a.mp3", "mp3", 1000L, null, null));
        // 第一次转存失败，第二次成功
        when(audioTransferService.transfer(anyLong(), anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.AUDIO_STORAGE_FAILED, "oss transient"))
                .thenReturn(new StoredObject("audio/u/s/a.mp3", "/media/audio/u/s/a.mp3", "mp3", 1024L));

        GenerationTask done = driveUntil(taskId, GenerationTask.STATUS_COMPLETED, 200);
        assertThat(done.getStatus()).isEqualTo(GenerationTask.STATUS_COMPLETED);
        // 核心断言：TTS 只提交一次（storage 重试不重复付费生成）
        verify(audioProvider, times(1)).submit(any());

        AudioAsset asset = audioAssetRepository
                .findTopByStoryIdAndStatusNotOrderByIdDesc(done.getStoryId(), AudioAsset.STATUS_SUPERSEDED)
                .orElseThrow();
        assertThat(asset.getStatus()).isEqualTo(AudioAsset.STATUS_READY);
        assertThat(asset.getStorageKey()).isEqualTo("audio/u/s/a.mp3");
        // H-03：转存成功后清理 provider 临时 URL
        assertThat(asset.getProviderUrl()).isNull();
    }

    @Test
    void storageExhaustionFailsTaskAndAsset() throws Exception {
        String token = login("audio-store-fail");
        long goalId = createGoal(token);
        long taskId = startTask(token, goalId);

        when(audioProvider.submit(any())).thenReturn(new AudioSubmitResult("prov-2"));
        when(audioProvider.query("prov-2")).thenReturn(new AudioQueryResult(
                AudioQueryResult.JobStatus.READY, "https://provider.example/b.mp3", "mp3", 1000L, null, null));
        when(audioTransferService.transfer(anyLong(), anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.AUDIO_STORAGE_FAILED, "oss down"));

        GenerationTask failed = driveUntil(taskId, GenerationTask.STATUS_FAILED, 400);
        assertThat(failed.getErrorStage()).isEqualTo(GenerationTask.STAGE_STORAGE);
        verify(audioProvider, times(1)).submit(any());

        AudioAsset asset = audioAssetRepository
                .findTopByStoryIdAndStatusNotOrderByIdDesc(failed.getStoryId(), AudioAsset.STATUS_SUPERSEDED)
                .orElseThrow();
        assertThat(asset.getStatus()).isEqualTo(AudioAsset.STATUS_FAILED);
    }

    @Test
    void regenerationKeepsOldReadyThenSwitches() throws Exception {
        String token = login("audio-bluegreen");
        long goalId = createGoal(token);
        long taskId = startTask(token, goalId);

        when(audioProvider.submit(any())).thenReturn(
                new AudioSubmitResult("prov-old"), new AudioSubmitResult("prov-new"));
        when(audioProvider.query("prov-old")).thenReturn(new AudioQueryResult(
                AudioQueryResult.JobStatus.READY, "https://provider.example/old.mp3", "mp3", 1000L, null, null));
        when(audioProvider.query("prov-new"))
                .thenReturn(new AudioQueryResult(AudioQueryResult.JobStatus.PROCESSING, null, null, null, null, null))
                .thenReturn(new AudioQueryResult(
                        AudioQueryResult.JobStatus.READY, "https://provider.example/new.mp3", "mp3", 2000L, null, null));
        when(audioTransferService.transfer(anyLong(), anyLong(), any()))
                .thenReturn(new StoredObject("audio/u/s/old.mp3", "/media/audio/u/s/old.mp3", "mp3", 1024L))
                .thenReturn(new StoredObject("audio/u/s/new.mp3", "/media/audio/u/s/new.mp3", "mp3", 2048L));

        GenerationTask first = driveUntil(taskId, GenerationTask.STATUS_COMPLETED, 200);
        long storyId = first.getStoryId();
        assertThat(storyAudioUrl(token, storyId)).isEqualTo("/media/audio/u/s/old.mp3");

        // 重新生成音频
        mockMvc.perform(post("/api/v1/stories/" + storyId + "/retry-audio")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 新音频在途（PROCESSING）：旧 READY 仍可播放
        orchestrator.processNext();
        orchestrator.processNext();
        GenerationTask inFlight = taskRepository.findById(taskId).orElseThrow();
        assertThat(inFlight.getStatus()).isEqualTo(GenerationTask.STATUS_AUDIO_PROCESSING);
        assertThat(storyAudioUrl(token, storyId)).isEqualTo("/media/audio/u/s/old.mp3");

        // 新音频 READY → 原子切换，旧 asset 变 SUPERSEDED
        GenerationTask done = driveUntil(taskId, GenerationTask.STATUS_COMPLETED, 200);
        assertThat(done.getStatus()).isEqualTo(GenerationTask.STATUS_COMPLETED);
        assertThat(storyAudioUrl(token, storyId)).isEqualTo("/media/audio/u/s/new.mp3");

        List<AudioAsset> assets = audioAssetRepository.findByStoryId(storyId);
        assertThat(assets).anyMatch(a -> "audio/u/s/old.mp3".equals(a.getStorageKey())
                && AudioAsset.STATUS_SUPERSEDED.equals(a.getStatus()));
        assertThat(assets).anyMatch(a -> "audio/u/s/new.mp3".equals(a.getStorageKey())
                && AudioAsset.STATUS_READY.equals(a.getStatus()));
        verify(audioProvider, times(2)).submit(any());
    }
}
