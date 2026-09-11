package io.github.javacodeintel.mcp.ports;
import io.github.javacodeintel.mcp.config.ProjectOptions; import io.github.javacodeintel.mcp.domain.JarArtifact; import java.io.IOException; import java.nio.file.Path; import java.util.List;
public interface DependencyProvider { List<JarArtifact> resolve(Path projectPath, ProjectOptions options) throws IOException; }
