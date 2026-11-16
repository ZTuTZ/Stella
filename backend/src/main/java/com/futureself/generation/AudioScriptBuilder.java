package com.futureself.generation;

import org.springframework.stereotype.Component;

/**
 * 音频旁白文本处理（实施文档 §15.6）：
 * 去除 Markdown、处理不适合朗读的符号；数字/英文保留交由供应商处理。
 * 禁止为追求“更像真人”自动插入没有依据的语气词。
 */
@Component
public class AudioScriptBuilder {

    public String build(String storyContent) {
        String text = storyContent == null ? "" : storyContent;
        text = text
                .replaceAll("```[\\s\\S]*?```", "")      // 代码块
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", "") // 图片
                .replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1") // 链接取文字
                .replaceAll("(?m)^#{1,6}\\s*", "")       // 标题井号
                .replaceAll("\\*{1,3}([^*]*)\\*{1,3}", "$1") // 加粗/斜体
                .replaceAll("[\\p{So}\\p{Cn}]", " ")     // emoji 等
                .replaceAll("[\\t\\r]", " ")
                .replaceAll(" {2,}", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
        return text;
    }
}
