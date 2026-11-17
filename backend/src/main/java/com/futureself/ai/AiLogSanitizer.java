package com.futureself.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 日志脱敏（H-07）：日志/ai_call_log 默认不得保存用户正文片段。
 * 只保留 operation、字符数、SHA-256 指纹等诊断信息，便于同一次请求关联而不泄露内容。
 */
public final class AiLogSanitizer {

    private static final int ERROR_MAX = 200;

    private AiLogSanitizer() {
    }

    /** 请求摘要：只含 operation / 字符数 / 指纹。 */
    public static Map<String, Object> promptSummary(String operation, String systemPrompt, String userPrompt) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("operation", operation == null ? "" : operation);
        summary.put("systemChars", length(systemPrompt));
        summary.put("userChars", length(userPrompt));
        summary.put("contextHash", fingerprint((systemPrompt == null ? "" : systemPrompt)
                + "\u0000" + (userPrompt == null ? "" : userPrompt)));
        return summary;
    }

    /** 长文本（故事正文/旁白）摘要：字符数 + 指纹。 */
    public static Map<String, Object> textSummary(String text) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("textChars", length(text));
        summary.put("textHash", fingerprint(text));
        return summary;
    }

    /** 日志用的短指纹：chars=123 hash=abcd1234。 */
    public static String describe(String text) {
        return "chars=" + length(text) + " hash=" + fingerprint(text);
    }

    /** 错误信息收敛：去换行、截断 200 字符（调用方仍不得写入正文）。 */
    public static String errorMessage(String message) {
        if (message == null) {
            return null;
        }
        String oneLine = message.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= ERROR_MAX ? oneLine : oneLine.substring(0, ERROR_MAX) + "…";
    }

    private static int length(String text) {
        return text == null ? 0 : text.length();
    }

    private static String fingerprint(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "unavailable";
        }
    }
}
