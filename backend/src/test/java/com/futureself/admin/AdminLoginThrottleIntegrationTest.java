package com.futureself.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H-11：连续错误密码达到阈值后拒绝；成功登录会清理失败计数。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminLoginThrottleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void clearThrottleKeys() {
        Set<String> keys = redis.keys("fs:admin:loginfail:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private org.springframework.test.web.servlet.ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void locksAfterConfiguredFailures() throws Exception {
        // test profile: 5 次阈值
        for (int i = 0; i < 5; i++) {
            login("wrong-password").andExpect(status().isUnauthorized());
        }
        login("wrong-password").andExpect(status().isTooManyRequests());
        login("test-admin-pass").andExpect(status().isTooManyRequests());
    }

    @Test
    void successResetsFailureCounter() throws Exception {
        for (int i = 0; i < 4; i++) {
            login("wrong-password").andExpect(status().isUnauthorized());
        }
        login("test-admin-pass").andExpect(status().isOk());
        // 计数已清理：再错 4 次仍不会锁定
        for (int i = 0; i < 4; i++) {
            login("wrong-password").andExpect(status().isUnauthorized());
        }
    }
}
