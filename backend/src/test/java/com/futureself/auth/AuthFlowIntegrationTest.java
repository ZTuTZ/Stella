package com.futureself.auth;

import com.futureself.user.domain.AppUser;
import com.futureself.user.domain.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2 验收（实施文档 §28）：
 * mock 登录拿 token；再次登录识别同一用户；onboarding 完成后登录返回完成态；
 * 生命周期接口只基于 token 当前用户（无跨用户寻址入口）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    private record Login(long userId, String token) {
    }

    private Login login(String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/wechat/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresIn").value(7200))
                .andExpect(jsonPath("$.data.user.id").isNumber())
                .andExpect(jsonPath("$.data.user.displayName").isNotEmpty())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        long userId = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.data.user.id")).longValue();
        String token = com.jayway.jsonpath.JsonPath.read(body, "$.data.accessToken");
        return new Login(userId, token);
    }

    @Test
    void mockLoginCreatesUserAndRecognizesSameUser() throws Exception {
        Login first = login("integration-code-001");
        Login second = login("integration-code-001");
        assertThat(second.userId()).isEqualTo(first.userId());

        Login other = login("integration-code-002");
        assertThat(other.userId()).isNotEqualTo(first.userId());
    }

    @Test
    void meRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void invalidTokenRejected() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void meReturnsUserAndSupportsDisplayNameUpdate() throws Exception {
        Login login = login("me-flow-code");
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("微信用户"))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false));

        mockMvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + login.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"小雨\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("小雨"));

        mockMvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + login.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"" + "x".repeat(31) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void profileRoundtripIncrementsVersion() throws Exception {
        Login login = login("profile-flow-code");
        String auth = "Bearer " + login.token();

        mockMvc.perform(get("/api/v1/me/life-profile").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileVersion").value(1))
                .andExpect(jsonPath("$.data.focusAreas.length()").value(0));

        String payload = """
                {
                  "currentSituation": "在设计公司做视觉，通勤两小时",
                  "focusAreas": ["事业", "自信"],
                  "importantPeople": [{"name":"阿澄","relation":"好友","note":"一起做播客"}],
                  "preferences": {"workspace": "有大窗户的家"}
                }
                """;
        mockMvc.perform(put("/api/v1/me/life-profile").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileVersion").value(2))
                .andExpect(jsonPath("$.data.focusAreas[0]").value("事业"))
                .andExpect(jsonPath("$.data.importantPeople[0].name").value("阿澄"))
                .andExpect(jsonPath("$.data.preferences.workspace").value("有大窗户的家"));

        mockMvc.perform(put("/api/v1/me/life-profile").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentSituation\":\"更新后的状态\",\"focusAreas\":[\"旅行\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileVersion").value(3));
    }

    @Test
    void profileValidationFailures() throws Exception {
        Login login = login("profile-invalid-code");
        String auth = "Bearer " + login.token();

        mockMvc.perform(put("/api/v1/me/life-profile").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"focusAreas\":[\"事业\",\"财富\",\"爱情\",\"家庭\",\"自信\",\"旅行\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(put("/api/v1/me/life-profile").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"focusAreas\":[\"玄学\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(put("/api/v1/me/life-profile").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentSituation\":\"" + "长".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String ninePeople = java.util.stream.IntStream.rangeClosed(1, 9)
                .mapToObj(i -> "{\"name\":\"p" + i + "\",\"relation\":\"friend\"}")
                .reduce((a, b) -> a + "," + b).orElseThrow();
        mockMvc.perform(put("/api/v1/me/life-profile").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"importantPeople\":[" + ninePeople + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void onboardingCompletePersistsAcrossLogins() throws Exception {
        Login login = login("onboarding-flow-code");
        mockMvc.perform(post("/api/v1/me/onboarding/complete").header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true));

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true));

        // 再次登录（同一用户）返回已完成 onboarding → 前端据此直接进首页
        MvcResult result = mockMvc.perform(post("/api/v1/auth/wechat/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"onboarding-flow-code\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Boolean completed = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.user.onboardingCompleted");
        assertThat(completed).isTrue();
    }

    @Test
    void profilesAreIsolatedPerUser() throws Exception {
        Login userA = login("isolation-code-a");
        Login userB = login("isolation-code-b");

        mockMvc.perform(put("/api/v1/me/life-profile")
                        .header("Authorization", "Bearer " + userA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentSituation\":\"A 的状态\",\"focusAreas\":[\"事业\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileVersion").value(2));

        // B 更新自己的档案，不影响 A
        mockMvc.perform(put("/api/v1/me/life-profile")
                        .header("Authorization", "Bearer " + userB.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentSituation\":\"B 的状态\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me/life-profile").header("Authorization", "Bearer " + userA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentSituation").value("A 的状态"))
                .andExpect(jsonPath("$.data.profileVersion").value(2));
    }

    @Test
    void disabledUserRejectedWith403() throws Exception {
        Login login = login("disabled-code");
        AppUser user = userRepository.findById(login.userId()).orElseThrow();
        user.setStatus("DISABLED");
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_DISABLED"));
    }
}
