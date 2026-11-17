package com.futureself.home;

import com.futureself.affirmation.AffirmationService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H-01：Affirmation 故障不得拖垮首页（必须 200 + 空列表，而不是 NPE/500）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomeAffirmationFailureTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AffirmationService affirmationService;

    private String login() throws Exception {
        String code = "home-aff-fail-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/wechat/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    @Test
    void affirmationFailureDoesNotBreakHome() throws Exception {
        String token = login();
        given(affirmationService.today(org.mockito.ArgumentMatchers.anyLong()))
                .willThrow(new IllegalStateException("simulated affirmation outage"));

        mockMvc.perform(get("/api/v1/home").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user").isNotEmpty())
                .andExpect(jsonPath("$.data.affirmations").isNotEmpty())
                .andExpect(jsonPath("$.data.affirmations.items").isArray())
                .andExpect(jsonPath("$.data.affirmations.items.length()").value(0))
                .andExpect(jsonPath("$.data.affirmations.date").isNotEmpty())
                .andExpect(jsonPath("$.data.hasRunningTask").value(false));
    }

    @Test
    void affirmationSuccessStillWorks() throws Exception {
        String token = login();
        given(affirmationService.today(org.mockito.ArgumentMatchers.anyLong()))
                .willReturn(new com.futureself.affirmation.api.AffirmationResponse(
                        "2026-11-17", java.util.List.of("我可以慢慢来")));

        MvcResult result = mockMvc.perform(get("/api/v1/home").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("我可以慢慢来");
    }
}
