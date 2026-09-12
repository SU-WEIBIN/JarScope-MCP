package io.github.javacodeintel.mcp.domain;

import java.nio.file.Path;

public record DecompiledResource(String entryPath, Path path, String type, String namespace) { }
