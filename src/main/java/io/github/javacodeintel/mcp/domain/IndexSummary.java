package io.github.javacodeintel.mcp.domain;
public record IndexSummary(long modules, long jarsDiscovered, long jarsIndexed, long jarsReused, long classes, long methods, long fields, long calls, long errors, long elapsedMs, java.util.List<IndexDiagnostic> diagnostics) {
    public IndexSummary { diagnostics = diagnostics == null ? java.util.List.of() : java.util.List.copyOf(diagnostics); }
    public IndexSummary(long modules, long jars, long classes, long methods, long fields, long calls, long errors) { this(modules, jars, jars, 0, classes, methods, fields, calls, errors, 0, java.util.List.of()); }
    public IndexSummary(long modules, long jarsDiscovered, long jarsIndexed, long jarsReused, long classes, long methods, long fields, long calls, long errors, long elapsedMs) { this(modules, jarsDiscovered, jarsIndexed, jarsReused, classes, methods, fields, calls, errors, elapsedMs, java.util.List.of()); }
    public long jars() { return jarsDiscovered; }
}
