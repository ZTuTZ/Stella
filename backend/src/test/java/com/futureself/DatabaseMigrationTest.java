package com.futureself;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 验收：新空库启动自动迁移成功，表结构与索引正确；
 * flyway_schema_history 中每个版本只记录一次（二次启动不重复执行）。
 */
@SpringBootTest
@ActiveProfiles("test")
class DatabaseMigrationTest {

    private static final List<String> V1_TABLES = List.of(
            "app_user", "life_profile", "manifestation_goal", "story", "audio_asset",
            "generation_task", "affirmation_daily", "prompt_template", "ai_call_log",
            "admin_user", "user_event");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allV1TablesExist() {
        for (String table : V1_TABLES) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                    Integer.class, table);
            assertThat(count).as("table %s should exist", table).isEqualTo(1);
        }
    }

    @Test
    void v1RecordedOnceInFlywayHistory() {
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1",
                Integer.class);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void keyIndexesExist() {
        record IndexCheck(String table, String index) {
        }
        List<IndexCheck> checks = List.of(
                new IndexCheck("app_user", "uk_user_openid"),
                new IndexCheck("life_profile", "uk_profile_user"),
                new IndexCheck("manifestation_goal", "idx_goal_user_status"),
                new IndexCheck("story", "idx_story_user_status"),
                new IndexCheck("audio_asset", "idx_audio_story"),
                new IndexCheck("generation_task", "idx_task_status_retry"),
                new IndexCheck("affirmation_daily", "uk_affirmation_user_date"),
                new IndexCheck("prompt_template", "uk_prompt_key_version"),
                new IndexCheck("admin_user", "uk_admin_username"));
        for (IndexCheck check : checks) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                            + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                    Integer.class, check.table(), check.index());
            assertThat(count).as("index %s on %s should exist", check.index(), check.table()).isEqualTo(1);
        }
    }
}
