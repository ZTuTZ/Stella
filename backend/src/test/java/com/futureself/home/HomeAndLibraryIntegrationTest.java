package com.futureself.home;

import com.futureself.generation.GenerationOrchestrator;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 7 验收（实施文档 §28）：
 * 同一用户同一天只生成一组 Affirmation（含并发）；
 * 首页 1 个接口可拿主数据；
 * 故事列表分页正确 + 收藏/软删除。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomeAndLibraryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GenerationOrchestrator orchestrator;

    @Autowired
    private GenerationTaskRepository taskRepository;

    @Autowired
    private StoryRepository storyRepository;

    private String login(String base) throws Exception {
        String code = base + "-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/wechat/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private long createCompletedStory(String token, String titleSuffix) throws Exception {
        long goalId = createGoal(token);
        MvcResult result = mockMvc.perform(post("/api/v1/goals/" + goalId + "/generate-story")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long taskId = ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.taskId")).longValue();
        awaitStatus(taskId, GenerationTask.STATUS_COMPLETED, 20000);
        GenerationTask task = taskRepository.findById(taskId).orElseThrow();
        Story story = storyRepository.findById(task.getStoryId()).orElseThrow();
        // 标题加后缀便于分页断言
        story.setTitle("故事-" + titleSuffix);
        storyRepository.save(story);
        return story.getId();
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

    private void awaitStatus(long taskId, String targetStatus, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            orchestrator.processNext();
            GenerationTask task = taskRepository.findById(taskId).orElseThrow();
            if (task.getStatus().equals(targetStatus)) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        throw new AssertionError("timeout waiting " + targetStatus);
    }

    @Test
    void affirmationTodayIsIdempotentIncludingConcurrent() throws Exception {
        String token = login("affirmation-idem");

        // 第一次：生成；第二次：复用
        MvcResult first = mockMvc.perform(get("/api/v1/affirmations/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(6))
                .andReturn();
        String date1 = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.data.date");
        List<String> items = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.data.items");

        MvcResult second = mockMvc.perform(get("/api/v1/affirmations/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(com.jayway.jsonpath.JsonPath.<List<String>>read(
                second.getResponse().getContentAsString(), "$.data.items")).isEqualTo(items);
        assertThat(com.jayway.jsonpath.JsonPath.<String>read(
                second.getResponse().getContentAsString(), "$.data.date")).isEqualTo(date1);

        // 并发 6 个请求：全部成功且只生成一份（UNIQUE(user_id, local_date) 兜底）
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            futures.add(pool.submit(() -> {
                latch.await();
                MvcResult r = mockMvc.perform(get("/api/v1/affirmations/today")
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn();
                return com.jayway.jsonpath.JsonPath.<List<Object>>read(
                        r.getResponse().getContentAsString(), "$.data.items").size();
            }));
        }
        latch.countDown();
        for (Future<Integer> f : futures) {
            assertThat(f.get()).isEqualTo(6);
        }
        pool.shutdown();
    }

    @Test
    void storyListPaginationFavoriteAndDelete() throws Exception {
        String token = login("library-flow");
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            ids.add(createCompletedStory(token, String.valueOf(i)));
        }

        // 分页：size=2 → 2/2/1，按创建时间倒序
        var page0 = mockMvc.perform(get("/api/v1/stories?page=0&size=2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andReturn();
        String firstTitle = com.jayway.jsonpath.JsonPath.read(
                page0.getResponse().getContentAsString(), "$.data.items[0].title");
        assertThat(firstTitle).isEqualTo("故事-5");

        mockMvc.perform(get("/api/v1/stories?page=2&size=2").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));

        // 收藏：标 2 条 → favorite=true 过滤
        mockMvc.perform(put("/api/v1/stories/" + ids.get(0) + "/favorite")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"favorite\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorite").value(true));
        mockMvc.perform(put("/api/v1/stories/" + ids.get(1) + "/favorite")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/stories?favorite=true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        // 软删除：列表消失，详情 404
        mockMvc.perform(delete("/api/v1/stories/" + ids.get(2))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/stories?page=0&size=20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.totalElements").value(4));
        mockMvc.perform(get("/api/v1/stories/" + ids.get(2))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void homeAggregatesMainData() throws Exception {
        String token = login("home-flow");
        long storyId = createCompletedStory(token, "home");

        mockMvc.perform(get("/api/v1/home").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.displayName").isNotEmpty())
                .andExpect(jsonPath("$.data.todayStory.id").value((int) storyId))
                .andExpect(jsonPath("$.data.recentStories.length()").value(1))
                .andExpect(jsonPath("$.data.affirmations.items.length()").value(6))
                .andExpect(jsonPath("$.data.hasRunningTask").value(false));

        // 创建运行中任务 → hasRunningTask=true
        long goalId = createGoal(token);
        mockMvc.perform(post("/api/v1/goals/" + goalId + "/generate-story")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/home").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.hasRunningTask").value(true));
    }
}
