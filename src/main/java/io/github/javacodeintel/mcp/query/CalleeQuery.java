package io.github.javacodeintel.mcp.query;

import java.util.Objects;

/** Filters and paging options for a method's outgoing call edges. */
public record CalleeQuery(
        PageRequest page,
        CallCategory category,
        String packagePrefix,
        String nameQuery,
        boolean groupByTarget) {

    public CalleeQuery {
        page = Objects.requireNonNull(page, "page");
        category = category == null ? CallCategory.ALL : category;
        packagePrefix = blankToNull(packagePrefix);
        nameQuery = blankToNull(nameQuery);
    }

    public static CalleeQuery defaults() {
        return new CalleeQuery(PageRequest.defaults(), CallCategory.ALL, null, null, true);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
