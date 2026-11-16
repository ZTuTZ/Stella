package com.futureself.admin;

import com.futureself.ai.PromptTemplateService;
import com.futureself.generation.GenerationOrchestrator;
import com.futureself.generation.StoryGenerationResult;
import com.futureself.generation.domain.GenerationTask;
import com.futureself.generation.domain.GenerationTaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 8 验收（实施文档 §28）：
 * 后台可定位任务/可重试（含只重试音频）/可切 Prompt 版本/可封禁用户；
 * 用户 token 不能访问 admin 接口。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GenerationOrchestrator orchestrator;

    @Autowired
    private GenerationTaskRepository taskRepository;

    @Autowired
    private PromptTemplateService promptTemplateService;

    @MockitoBean
    private com.futureself.ai.text.TextAiProvider textAiProvider;

    private String adminLogin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"test-admin-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.admin.role").value("ADMIN"))
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private String adminLoginWrong() throws Exception {
        return mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
    }

    private String userLogin(String base) throws Exception {
        String code = base + "-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/wechat/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private long createCompletedTask(String userToken) throws Exception {
        long goalId = ((Number) com.jayway.jsonpath.JsonPath.read(mockMvc.perform(post("/api/v1/goals")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goalCategory":"事业","goalTitle":"拥有一间自己的设计工作室",
                                 "goalDescription":"一年后开一间小而稳的设计工作室，收入更稳定。"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();

        MvcResult result = mockMvc.perform(post("/api/v1/goals/" + goalId + "/generate-story")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();
        long taskId = ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.taskId")).longValue();

        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline) {
            orchestrator.processNext();
            GenerationTask task = taskRepository.findById(taskId).orElseThrow();
            if (GenerationTask.STATUS_COMPLETED.equals(task.getStatus())) {
                return taskId;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        throw new AssertionError("task not completed in time");
    }

    @Test
    void adminAuthAndPermissionBoundary() throws Exception {
        // 密码错误
        String wrongBody = adminLoginWrong();
        assertThat(wrongBody).contains("AUTH_REQUIRED");

        // 小程序用户 token 访问 admin 接口 → 403
        String userToken = userLogin("admin-boundary-user");
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

        // admin token → 200 分页
        String adminToken = adminLogin();
        mockMvc.perform(get("/api/v1/admin/users?page=0&size=10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void banUserBlocksTheirTokens() throws Exception {
        String userToken = userLogin("admin-ban-user");
        long userId = ((Number) com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + userToken))
                        .andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();

        String adminToken = adminLogin();
        mockMvc.perform(put("/api/v1/admin/users/" + userId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        // 被封禁用户的 token 立即失效（USER_DISABLED）
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_DISABLED"));

        // 解封恢复
        mockMvc.perform(put("/api/v1/admin/users/" + userId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    @Test
    void promptVersionSwitch() throws Exception {
        String adminToken = adminLogin();
        // 记录当前启用的 FOLLOWUP 版本（seed v1），测试结束恢复
        long originalId = promptTemplateService.getEnabled("FOLLOWUP").getId();
        int originalVersion = promptTemplateService.getEnabled("FOLLOWUP").getVersion();

        // 新建 FOLLOWUP vNext（默认禁用；测试库跨 run 持久化，版本号动态取）
        MvcResult created = mockMvc.perform(post("/api/v1/admin/prompts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"promptKey":"FOLLOWUP","systemPrompt":"V2 系统提示","userTemplate":"V2 用户模板 {{goalTitle}}","notes":"test v2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andReturn();
        long vNextId = ((Number) com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.data.id")).longValue();
        int vNextVersion = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.data.version");

        // 启用 vNext → 当前启用版本为 vNext
        mockMvc.perform(put("/api/v1/admin/prompts/" + vNextId + "/enable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));
        assertThat(promptTemplateService.getEnabled("FOLLOWUP").getVersion()).isEqualTo(vNextVersion);

        // 切回原版本 → 同 key 仅一个 enabled（§14.4）
        mockMvc.perform(put("/api/v1/admin/prompts/" + originalId + "/enable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        assertThat(promptTemplateService.getEnabled("FOLLOWUP").getVersion()).isEqualTo(originalVersion);
        mockMvc.perform(get("/api/v1/admin/prompts/" + vNextId).header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void retryCompletedTaskRegeneratesAudio() throws Exception {
        when(textAiProvider.providerName()).thenReturn("mock");
        when(textAiProvider.generateStructured(any(), eq(StoryGenerationResult.class)))
                .thenReturn(new StoryGenerationResult("后台重试故事", "摘要", "正文……", null));

        String userToken = userLogin("admin-retry-user");
        long taskId = createCompletedTask(userToken);
        String adminToken = adminLogin();

        // 运行中任务不可重试（已完成是终态，可重试）
        MvcResult retried = mockMvc.perform(post("/api/v1/admin/tasks/" + taskId + "/retry")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AUDIO_SUBMITTING"))
                .andExpect(jsonPath("$.data.retryCount").value(0))
                .andReturn();

        // 继续推进 → 再次 COMPLETED（新音频，旧音频 SUPERSEDED）
        long deadline = System.currentTimeMillis() + 20000;
        GenerationTask task = null;
        while (System.currentTimeMillis() < deadline) {
            orchestrator.processNext();
            task = taskRepository.findById(taskId).orElseThrow();
            if (GenerationTask.STATUS_COMPLETED.equals(task.getStatus())) {
                break;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        assertThat(task).isNotNull();
        assertThat(task.getStatus()).isEqualTo(GenerationTask.STATUS_COMPLETED);
        assertThat(task.getStoryId()).isNotNull();
    }

    @Test
    void dashboardSummaryAndAiLogs() throws Exception {
        String adminToken = adminLogin();
        mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").isNumber())
                .andExpect(jsonPath("$.data.failedTasks").isNumber())
                .andExpect(jsonPath("$.data.todayActiveUsers").isNumber());

        mockMvc.perform(get("/api/v1/admin/ai-logs?page=0&size=5")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }
}
