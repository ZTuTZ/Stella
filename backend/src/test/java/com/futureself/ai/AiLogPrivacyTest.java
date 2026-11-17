package com.futureself.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H-07：ai_call_log 不得保存用户正文片段，只保留 operation / 长度 / 指纹。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiLogPrivacyTest {

    private static final String SECRET_MARKER = "SECRET_MARKER_123";
    private static final String PRIVATE_PERSON = "PRIVATE_PERSON_NAME";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aiCallLogDoesNotPersistPromptContent() throws Exception {
        String token = login();
        mockMvc.perform(put("/api/v1/me/life-profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentSituation":"%s 住在杭州，正在准备自己的工作室。",
                                 "focusAreas":["事业"],
                                 "importantPeople":[{"name":"%s","relation":"好友","note":"一起做播客"}]}
                                """.formatted(SECRET_MARKER, PRIVATE_PERSON)))
                .andExpect(status().isOk());

        long goalId = createGoal(token);
        mockMvc.perform(post("/api/v1/goals/" + goalId + "/followup-questions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT request_summary_json, response_summary_json, error_message FROM ai_call_log "
                        + "WHERE provider_type = 'TEXT' ORDER BY id DESC LIMIT 5");
        assertThat(rows).isNotEmpty();
        for (Map<String, Object> row : rows) {
            String summary = String.valueOf(row.get("request_summary_json"));
            String response = String.valueOf(row.get("response_summary_json"));
            assertThat(summary).doesNotContain(SECRET_MARKER).doesNotContain(PRIVATE_PERSON);
            assertThat(response).doesNotContain(SECRET_MARKER).doesNotContain(PRIVATE_PERSON);
            if (row.get("error_message") != null) {
                assertThat(String.valueOf(row.get("error_message")))
                        .doesNotContain(SECRET_MARKER).doesNotContain(PRIVATE_PERSON);
            }
        }
        // 仍保留可诊断字段
        String latest = String.valueOf(rows.get(0).get("request_summary_json"));
        assertThat(latest).contains("contextHash").contains("userChars");
    }

    private String login() throws Exception {
        String code = "privacy-" + UUID.randomUUID().toString().substring(0, 8);
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
}
