package io.github.javacodeintel.mcp.domain;

import java.util.List;

/** Result of Maven workspace and external dependency resolution. */
public record DependencyResolution(
        MavenReactorModel reactor,
        List<JarArtifact> jars,
        List<IndexDiagnostic> diagnostics) {
    public DependencyResolution {
        reactor = java.util.Objects.requireNonNull(reactor, "reactor");
        jars = jars == null ? List.of() : List.copyOf(jars);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
