package io.github.javacodeintel.mcp.query;

import io.github.javacodeintel.mcp.domain.CallEdge;

import java.util.Objects;

/** A call edge enriched with a navigation category and aggregation count. */
public record CallEdgeView(CallEdge edge, CallCategory category, long callSiteCount, String reason) {
    public CallEdgeView {
        edge = Objects.requireNonNull(edge, "edge");
        category = category == null ? CallCategory.UNKNOWN : category;
        if (callSiteCount < 1) throw new IllegalArgumentException("callSiteCount must be positive");
        reason = reason == null ? "" : reason;
    }
}
