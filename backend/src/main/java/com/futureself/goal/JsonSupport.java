package com.futureself.goal;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.MapType;

import java.util.Map;

/**
 * details_json 的 Map 解析/序列化辅助（Jackson 3）。
 */
final class JsonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonSupport() {
    }

    static Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            MapType type = MAPPER.getTypeFactory().constructMapType(java.util.LinkedHashMap.class, String.class, Object.class);
            return MAPPER.readValue(json, type);
        } catch (JacksonException e) {
            return Map.of();
        }
    }

    static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("json serialize failed", e);
        }
    }
}
