package io.github.javacodeintel.mcp.query;

public record CallCategoryCount(CallCategory category, long count) {
    public CallCategoryCount {
        if (category == null || category == CallCategory.ALL) throw new IllegalArgumentException("category must be a concrete category");
        if (count < 0) throw new IllegalArgumentException("count must not be negative");
    }
}
