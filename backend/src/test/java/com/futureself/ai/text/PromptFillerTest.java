package com.futureself.ai.text;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prompt 填充与截断（§33）。
 */
class PromptFillerTest {

    @Test
    void fillsPlaceholdersAndHandlesMissing() {
        String filled = PromptFiller.fill(
                "标题：{{goalTitle}}；时间：{{targetTimeText}}；未知：{{unknown}}",
                Map.of("goalTitle", "拥有自己的工作室"));
        // 已提供的占位符被替换；未提供的保留原样（组装侧保证不发送空字段）
        assertThat(filled).isEqualTo("标题：拥有自己的工作室；时间：{{targetTimeText}}；未知：{{unknown}}");
    }

    @Test
    void truncatesLongText() {
        assertThat(PromptFiller.truncate(null, 10)).isEmpty();
        assertThat(PromptFiller.truncate("short", 10)).isEqualTo("short");
        String longText = "很长的文本".repeat(20);
        String truncated = PromptFiller.truncate(longText, 10);
        assertThat(truncated).hasSize(11).endsWith("…");
    }
}
