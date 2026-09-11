package io.github.javacodeintel.mcp.domain;
public record DecompileCacheKey(String jarIdentity, String classEntry, int targetJavaRelease, String decompilerVersion) { }
