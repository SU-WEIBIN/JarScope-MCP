package io.github.javacodeintel.mcp.domain;
import java.nio.file.Path;
import java.util.Objects;
public record JarArtifact(Path path, MavenCoordinate coordinate) { public JarArtifact { Objects.requireNonNull(path, "path"); Objects.requireNonNull(coordinate, "coordinate"); } public String fileName() { Path p=path.getFileName(); return p==null?path.toString():p.toString(); } }
