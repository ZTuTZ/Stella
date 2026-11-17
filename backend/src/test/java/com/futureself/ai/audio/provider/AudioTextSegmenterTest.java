package com.futureself.ai.audio.provider;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 方案 ②：分段与 MP3 拼接的纯逻辑测试。
 */
class AudioTextSegmenterTest {

    @Test
    void respectsMaxChars() {
        String text = "今天清晨，你从自己的公寓醒来，窗外的光落在木质书桌上，空气里有咖啡的味道。".repeat(15);
        List<String> segments = AudioTextSegmenter.split(text);
        assertThat(segments).isNotEmpty();
        assertThat(segments).allMatch(s -> s.length() <= AudioTextSegmenter.MAX_CHARS);
        assertThat(String.join("", segments).replaceAll("\\s", ""))
                .isEqualTo(text.replaceAll("\\s", ""));
    }

    @Test
    void prefersSentenceBoundaries() {
        String text = "第一句话在这里。第二句话稍微长一点，它也应该被正确切分！第三句？";
        List<String> segments = AudioTextSegmenter.split(text);
        assertThat(segments).allMatch(s -> s.length() <= AudioTextSegmenter.MAX_CHARS);
        // 单段即可容纳时不应拆分
        assertThat(segments).hasSize(1);
    }

    @Test
    void splitsLongSentenceWithoutPunctuation() {
        String text = "长".repeat(400);
        List<String> segments = AudioTextSegmenter.split(text);
        assertThat(segments).hasSize(3);
        assertThat(segments).allMatch(s -> s.length() <= AudioTextSegmenter.MAX_CHARS);
    }

    @Test
    void handlesBlankText() {
        assertThat(AudioTextSegmenter.split(null)).isEmpty();
        assertThat(AudioTextSegmenter.split("   ")).isEmpty();
        assertThat(AudioTextSegmenter.split("\n\n")).isEmpty();
    }
}
