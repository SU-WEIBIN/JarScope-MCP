package io.github.javacodeintel.mcp.query;

import io.github.javacodeintel.mcp.domain.CallEdge;

import java.util.Locale;

/** Classifies static call edges using transparent, deterministic naming rules. */
public final class CallEdgeClassifier {
    public CallEdgeView classify(CallEdge edge) {
        if (edge == null) throw new IllegalArgumentException("edge is required");
        String owner = lower(edge.targetOwner());
        String name = lower(edge.targetName());
        if (owner.isBlank() || name.isBlank()) {
            return new CallEdgeView(edge, CallCategory.UNKNOWN, 1, "target signature is incomplete");
        }
        if (isFramework(owner)) {
            return new CallEdgeView(edge, CallCategory.FRAMEWORK_OR_UTILITY, 1, "target is a JDK or common framework type");
        }
        if (owner.endsWith("mapper") || owner.endsWith("repository") || containsAny(name, "query", "select", "insert", "update", "delete", "find", "count")) {
            return new CallEdgeView(edge, CallCategory.DATA_ACCESS, 1, owner.endsWith("mapper") ? "target class ends with Mapper" : "method name suggests data access");
        }
        if (containsAny(owner, "export", "batch") || containsAny(name, "export", "batch", "excel", "file", "write")) {
            return new CallEdgeView(edge, CallCategory.EXPORT_OR_BATCH, 1, "target name suggests export or batch processing");
        }
        if (containsAny(owner, "bom", "part") || containsAny(name, "bom", "expand", "tree", "parallel")) {
            return new CallEdgeView(edge, CallCategory.BOM_EXPANSION, 1, "target name suggests BOM expansion");
        }
        return new CallEdgeView(edge, CallCategory.BUSINESS, 1, "non-framework project-looking target");
    }

    private static boolean isFramework(String owner) {
        return owner.startsWith("java.") || owner.startsWith("javax.") || owner.startsWith("jakarta.")
                || owner.startsWith("jdk.") || owner.startsWith("sun.") || owner.startsWith("org.springframework.")
                || owner.startsWith("org.slf4j.") || owner.startsWith("org.apache.") || owner.startsWith("com.fasterxml.")
                || owner.startsWith("lombok.") || owner.startsWith("kotlin.") || owner.startsWith("scala.");
    }

    private static boolean containsAny(String value, String... words) {
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
