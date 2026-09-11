package io.github.javacodeintel.mcp.ports;

import io.github.javacodeintel.mcp.domain.DecompiledSource;
import io.github.javacodeintel.mcp.domain.JarArtifact;

import java.io.IOException;
import java.nio.file.Path;

public interface ClassDecompiler {
    DecompiledSource decompile(Path projectPath, JarArtifact jar, String className,
                                int targetJavaRelease, boolean force) throws IOException;
}
