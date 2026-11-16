package com.futureself.ai.text;

import java.util.Map;

/**
 * {{placeholder}} 填充工具：占位符 = {{key}}；缺失 key 替换为空串（§33：空字段不发送）。
 */
public final class PromptFiller {

    private PromptFiller() {
    }

    public static String fill(String template, Map<String, String> placeholders) {
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    /** 截断长文本，附加省略标记。 */
    public static String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxChars) + "…";
    }
}
