package io.github.javacodeintel.mcp.ports;
import io.github.javacodeintel.mcp.config.ProjectOptions;
import io.github.javacodeintel.mcp.domain.DependencyResolution;

import java.io.IOException;
import java.nio.file.Path;

public interface DependencyProvider {
    DependencyResolution resolve(Path projectPath, ProjectOptions options) throws IOException;
}
