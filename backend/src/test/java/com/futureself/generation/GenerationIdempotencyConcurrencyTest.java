package com.futureself.generation;

import com.futureself.generation.domain.GenerationTask;
import com.futureself.generation.domain.GenerationTaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * H-06：持久幂等 + goal 行锁并发保护。
 * 1) 同 user + 同 key 并发 N 个 → 只 1 条 task，全部返回同一 taskId。
 * 2) 同 goal + 不同 key 并发 → 最多 1 个 active task，其余 409。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GenerationIdempotencyConcurrencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GenerationTaskRepository taskRepository;

    private String login(String base) throws Exception {
        String code = base + "-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/wechat/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
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
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private record Resp(int status, String body) {
    }

    private Resp generate(String token, long goalId, String key) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/goals/" + goalId + "/generate-story")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key))
                .andReturn();
        return new Resp(r.getResponse().getStatus(), r.getResponse().getContentAsString());
    }

    @Test
    void sameKeyConcurrentCreatesOnlyOneTask() throws Exception {
        String token = login("idem-conc");
        long goalId = createGoal(token);
        String key = "conc-" + UUID.randomUUID();

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Resp>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return generate(token, goalId, key);
            }));
        }
        start.countDown();
        List<Long> taskIds = new ArrayList<>();
        for (Future<Resp> f : futures) {
            Resp r = f.get(60, TimeUnit.SECONDS);
            assertThat(r.status()).as("body=%s", r.body()).isEqualTo(200);
            taskIds.add(((Number) com.jayway.jsonpath.JsonPath.read(r.body(), "$.data.taskId")).longValue());
        }
        pool.shutdown();

        assertThat(taskIds).containsOnly(taskIds.get(0));
        GenerationTask created = taskRepository.findById(taskIds.get(0)).orElseThrow();
        assertThat(created.getIdempotencyKey()).isEqualTo(key);
        assertThat(taskRepository.countByUserIdAndIdempotencyKey(created.getUserId(), key)).isEqualTo(1);
    }

    @Test
    void differentKeysConcurrentCreateAtMostOneActiveTask() throws Exception {
        String token = login("idem-diff");
        long goalId = createGoal(token);

        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Resp>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String key = "diff-" + i + "-" + UUID.randomUUID();
            futures.add(pool.submit(() -> {
                start.await();
                return generate(token, goalId, key);
            }));
        }
        start.countDown();
        int ok = 0;
        int conflict = 0;
        for (Future<Resp> f : futures) {
            Resp r = f.get(60, TimeUnit.SECONDS);
            if (r.status() == 200) {
                ok++;
            } else if (r.status() == 409) {
                conflict++;
            }
        }
        pool.shutdown();

        assertThat(ok).isEqualTo(1);
        assertThat(conflict).isEqualTo(threads - 1);
        assertThat(taskRepository.existsByGoalIdAndStatusNotIn(goalId,
                List.of(GenerationTask.STATUS_COMPLETED, GenerationTask.STATUS_FAILED, GenerationTask.STATUS_BLOCKED)))
                .isTrue();
    }
}
