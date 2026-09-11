package io.github.javacodeintel.mcp.storage;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

final class SqlValueCodec {
    private SqlValueCodec() {
    }

    static String encodeMap(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(quote(entry.getKey())).append(':').append(encode(entry.getValue()));
        }
        return json.append('}').toString();
    }

    static Map<String, Object> decodeMap(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return Map.of();
        }
        String value = json.trim();
        if (!value.startsWith("{") || !value.endsWith("}")) {
            return Map.of("raw", json);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        String body = value.substring(1, value.length() - 1).trim();
        if (body.isEmpty()) {
            return Map.of();
        }
        for (String pair : splitTopLevel(body, ',')) {
            int colon = indexOfTopLevel(pair, ':');
            if (colon < 0) {
                continue;
            }
            String key = unquote(pair.substring(0, colon).trim());
            result.put(key, decodeScalar(pair.substring(colon + 1).trim()));
        }
        return result;
    }

    private static String encode(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Collection<?> collection) {
            StringBuilder result = new StringBuilder("[");
            boolean first = true;
            for (Object item : collection) {
                if (!first) {
                    result.append(',');
                }
                first = false;
                result.append(encode(item));
            }
            return result.append(']').toString();
        }
        return quote(value.toString());
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1)
                    .replace("\\\"", "\"").replace("\\\\", "\\")
                    .replace("\\n", "\n").replace("\\r", "\r");
        }
        return value;
    }

    private static Object decodeScalar(String value) {
        if ("null".equals(value)) {
            return null;
        }
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return unquote(value);
        }
        try {
            return value.contains(".") ? Double.valueOf(value) : Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private static int indexOfTopLevel(String value, char target) {
        boolean quoted = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '"' && (i == 0 || value.charAt(i - 1) != '\\')) {
                quoted = !quoted;
            } else if (!quoted && current == target) {
                return i;
            }
        }
        return -1;
    }

    private static String[] splitTopLevel(String value, char delimiter) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        boolean quoted = false;
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '"' && (i == 0 || value.charAt(i - 1) != '\\')) {
                quoted = !quoted;
            } else if (!quoted && current == delimiter) {
                parts.add(value.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(value.substring(start));
        return parts.toArray(String[]::new);
    }
}
