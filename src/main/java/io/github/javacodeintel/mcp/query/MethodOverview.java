package io.github.javacodeintel.mcp.query;

import io.github.javacodeintel.mcp.domain.MethodInfo;

import java.util.List;
import java.util.Objects;

/** Bounded, evidence-backed information used to navigate a method call graph. */
public record MethodOverview(
        MethodInfo method,
        long callerCount,
        List<CallEdgeView> callerExamples,
        long calleeCallSiteCount,
        long uniqueCalleeCount,
        List<CallCategoryCount> categoryCounts,
        List<CallEdgeView> representativeCallees,
        List<String> warnings) {

    public MethodOverview {
        method = Objects.requireNonNull(method, "method");
        if (callerCount < 0 || calleeCallSiteCount < 0 || uniqueCalleeCount < 0) {
            throw new IllegalArgumentException("relation counts must not be negative");
        }
        callerExamples = callerExamples == null ? List.of() : List.copyOf(callerExamples);
        categoryCounts = categoryCounts == null ? List.of() : List.copyOf(categoryCounts);
        representativeCallees = representativeCallees == null ? List.of() : List.copyOf(representativeCallees);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
