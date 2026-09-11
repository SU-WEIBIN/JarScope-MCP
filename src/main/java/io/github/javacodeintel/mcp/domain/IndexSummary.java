package io.github.javacodeintel.mcp.domain;
public record IndexSummary(long modules, long jarsDiscovered, long jarsIndexed, long jarsReused, long classes, long methods, long fields, long calls, long errors, long elapsedMs) {
    public IndexSummary(long modules, long jars, long classes, long methods, long fields, long calls, long errors) { this(modules, jars, jars, 0, classes, methods, fields, calls, errors, 0); }
    public long jars() { return jarsDiscovered; }
}
