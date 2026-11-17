package com.futureself.ai.audio.provider;

import java.util.ArrayList;
import java.util.List;

/**
 * 旁白文本分段（方案 ②：基础语音合成 TextToVoice 单次中文 150 字上限）。
 *
 * <p>策略：先按句末标点（。！？!?；;\n）切句，再贪心打包为 ≤150 字符的段；
 * 单句超长时退化为按逗号（，、,）切，最后硬切。宁可多一段，不超限。
 */
public final class AudioTextSegmenter {

    /** 基础接口保守上限（中文按全角字符计；英文 500 字母，统一按 150 字符保守处理）。 */
    static final int MAX_CHARS = 150;

    private static final String SENTENCE_ENDERS = "。！？!?；;\n";
    private static final String SOFT_BREAKS = "，、, ";

    private AudioTextSegmenter() {
    }

    public static List<String> split(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        String cleaned = text.replace("\r\n", "\n").trim();
        if (cleaned.isEmpty()) {
            return result;
        }

        // 1) 切句（保留句末标点）
        List<String> sentences = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            buffer.append(c);
            if (SENTENCE_ENDERS.indexOf(c) >= 0) {
                sentences.add(buffer.toString().trim());
                buffer.setLength(0);
            }
        }
        if (buffer.length() > 0) {
            sentences.add(buffer.toString().trim());
        }

        // 2) 贪心打包
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            if (sentence.isEmpty()) {
                continue;
            }
            if (sentence.length() > MAX_CHARS) {
                flush(result, current);
                result.addAll(splitLongSentence(sentence));
                continue;
            }
            if (current.length() + sentence.length() > MAX_CHARS) {
                flush(result, current);
            }
            current.append(sentence);
        }
        flush(result, current);
        return result;
    }

    private static void flush(List<String> result, StringBuilder current) {
        String value = current.toString().trim();
        if (!value.isEmpty()) {
            result.add(value);
        }
        current.setLength(0);
    }

    /** 超长单句：按软标点切分，仍超长则硬切。 */
    private static List<String> splitLongSentence(String sentence) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < sentence.length(); i++) {
            char c = sentence.charAt(i);
            current.append(c);
            if (current.length() >= MAX_CHARS) {
                // 在软标点处断开（尽量在最后 30 字内找）
                int cut = findSoftBreak(current);
                if (cut > 0 && cut < current.length()) {
                    parts.add(current.substring(0, cut).trim());
                    String rest = current.substring(cut);
                    current.setLength(0);
                    current.append(rest);
                } else {
                    parts.add(current.toString().trim());
                    current.setLength(0);
                }
            }
        }
        flush(parts, current);
        return parts;
    }

    private static int findSoftBreak(StringBuilder current) {
        for (int i = current.length() - 1; i >= Math.max(0, current.length() - 30); i--) {
            if (SOFT_BREAKS.indexOf(current.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        return -1;
    }
}
