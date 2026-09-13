package io.github.javacodeintel.mcp.query;

/** A deterministic navigation hint for a static call edge. */
public enum CallCategory {
    ALL,
    BUSINESS,
    DATA_ACCESS,
    BOM_EXPANSION,
    EXPORT_OR_BATCH,
    FRAMEWORK_OR_UTILITY,
    UNKNOWN
}
