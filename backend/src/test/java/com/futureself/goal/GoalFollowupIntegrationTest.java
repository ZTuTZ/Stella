package com.futureself.goal;

import com.futureself.ai.PromptTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3 验收（实施文档 §28）：
 * Mock 完成完整追问；owner 隔离；CRUD 校验；Prompt seed 可用。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoalFollowupIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PromptTemplateService promptTemplateService;

    private String login(String base) throws Exception {
        // mock openid 按 code 确定性生成且测试库跨 run 持久化，故每个用例用唯一 code
        String code = base + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
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
                                 "goalDescription":"一年后开一间小而稳的设计工作室，收入更稳定，有更多自由时间。","targetTimeText":"一年后"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    @Test
    void seededPromptsAvailable() {
        assertThat(promptTemplateService.getEnabled("FOLLOWUP")).isNotNull();
        assertThat(promptTemplateService.getEnabled("STORY")).isNotNull();
        assertThat(promptTemplateService.getEnabled("AFFIRMATION")).isNotNull();
    }

    @Test
    void createGoalValidation() throws Exception {
        String token = login("goal-validation");

        mockMvc.perform(post("/api/v1/goals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalCategory\":\"玄学\",\"goalTitle\":\"合法标题\",\"goalDescription\":\"这是一个足够长的描述。\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/goals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalCategory\":\"事业\",\"goalTitle\":\"短\",\"goalDescription\":\"这是一个足够长的描述。\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/goals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalCategory\":\"事业\",\"goalTitle\":\"合法标题\",\"goalDescription\":\"太短\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void goalCrudAndSoftDelete() throws Exception {
        String token = login("goal-crud");
        long goalId = createGoal(token);

        mockMvc.perform(patch("/api/v1/goals/" + goalId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetTimeText\":\"2027 年底前\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetTimeText").value("2027 年底前"));

        mockMvc.perform(get("/api/v1/goals").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(delete("/api/v1/goals/" + goalId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/goals/" + goalId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOAL_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/goals").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void otherUsersGoalIsNotFound() throws Exception {
        String tokenA = login("goal-owner-a");
        String tokenB = login("goal-owner-b");
        long goalId = createGoal(tokenA);

        mockMvc.perform(get("/api/v1/goals/" + goalId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOAL_NOT_FOUND"));

        mockMvc.perform(delete("/api/v1/goals/" + goalId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void mockFollowupFullFlow() throws Exception {
        String token = login("followup-flow");

        // 先完善人生档案，让上下文组装有内容
        mockMvc.perform(put("/api/v1/me/life-profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentSituation":"在设计公司做视觉，通勤两小时",
                                 "focusAreas":["事业","自信"],
                                 "importantPeople":[{"name":"阿澄","relation":"好友","note":"一起做播客"}]}
                                """))
                .andExpect(status().isOk());

        long goalId = createGoal(token);

        MvcResult questions = mockMvc.perform(post("/api/v1/goals/" + goalId + "/followup-questions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions.length()").value(2))
                .andExpect(jsonPath("$.data.questions[0].id").value("q1"))
                .andExpect(jsonPath("$.data.questions[0].inputType").value("TEXT"))
                .andExpect(jsonPath("$.data.questions[0].optional").value(true))
                .andReturn();

        assertThat(questions.getResponse().getHeader("X-Request-Id")).isNotBlank();

        mockMvc.perform(put("/api/v1/goals/" + goalId + "/followup-answers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[{\"questionId\":\"q1\",\"answer\":\"上海一个有大窗户的家里\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.details.questions[0].id").value("q1"))
                .andExpect(jsonPath("$.data.details.answers[0].questionId").value("q1"))
                .andExpect(jsonPath("$.data.details.answers[0].answer").value("上海一个有大窗户的家里"));

        // GET 再次确认已持久化
        mockMvc.perform(get("/api/v1/goals/" + goalId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.details.answers.length()").value(1));
    }

    @Test
    void followupAnswersValidation() throws Exception {
        String token = login("followup-invalid");
        long goalId = createGoal(token);

        mockMvc.perform(put("/api/v1/goals/" + goalId + "/followup-answers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[{\"answer\":\"缺少 questionId\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
