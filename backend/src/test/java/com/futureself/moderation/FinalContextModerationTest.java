package com.futureself.moderation;

import com.futureself.generation.GenerationOrchestrator;
import com.futureself.generation.domain.GenerationTask;
import com.futureself.generation.domain.GenerationTaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H-09：profile / followup answer 中的高风险内容必须在最终 AI 调用前被拦截。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FinalContextModerationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GenerationOrchestrator orchestrator;

    @Autowired
    private GenerationTaskRepository taskRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @org.junit.jupiter.api.BeforeEach
    void ensureFollowupTemplateHasContext() {
        // 测试库模板可能被历史测试改写；恢复包含 profile 上下文的模板，保证 H-09 断言有效
        jdbcTemplate.update("UPDATE prompt_template SET user_template = ? "
                        + "WHERE prompt_key = 'FOLLOWUP' AND enabled = 1",
                "当前生活：{{currentSituation}}\n关注领域：{{focusAreas}}\n目标：{{goalTitle}}");
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

    private void setRiskyProfile(String token) throws Exception {
        mockMvc.perform(put("/api/v1/me/life-profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentSituation":"最近总是想到自杀这件事，想结束自己的生命。",
                                 "focusAreas":["事业","自信"],
                                 "importantPeople":[{"name":"阿澄","relation":"好友","note":"一起做播客"}]}
                                """))
                .andExpect(status().isOk());
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

    @Test
    void followupInputIsModerated() throws Exception {
        String token = login("mod-followup");
        setRiskyProfile(token);
        long goalId = createGoal(token);
        mockMvc.perform(post("/api/v1/goals/" + goalId + "/followup-questions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MODERATION_BLOCKED"));
    }

    @Test
    void storyInputIsBlockedBeforeLlm() throws Exception {
        String token = login("mod-story");
        setRiskyProfile(token);
        long goalId = createGoal(token);
        MvcResult r = mockMvc.perform(post("/api/v1/goals/" + goalId + "/generate-story")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "mod-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andReturn();
        long taskId = ((Number) com.jayway.jsonpath.JsonPath.read(
                r.getResponse().getContentAsString(), "$.data.taskId")).longValue();

        GenerationTask task = null;
        for (int i = 0; i < 60; i++) {
            orchestrator.processNext();
            task = taskRepository.findById(taskId).orElseThrow();
            if (GenerationTask.STATUS_BLOCKED.equals(task.getStatus())
                    || GenerationTask.STATUS_COMPLETED.equals(task.getStatus())) {
                break;
            }
        }
        assertThat(task).isNotNull();
        assertThat(task.getStatus()).isEqualTo(GenerationTask.STATUS_BLOCKED);
        assertThat(task.getStoryId()).isNull();
    }

    @Test
    void affirmationInputIsBlocked() throws Exception {
        String token = login("mod-affirmation");
        setRiskyProfile(token);
        mockMvc.perform(get("/api/v1/affirmations/today").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MODERATION_BLOCKED"));
    }
}
