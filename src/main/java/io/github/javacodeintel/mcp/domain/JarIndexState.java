package io.github.javacodeintel.mcp.domain;

import java.nio.file.Path;

public record JarIndexState(Path path,long size,long lastModified,String sha256,ChangeDetectionMode mode) { }
