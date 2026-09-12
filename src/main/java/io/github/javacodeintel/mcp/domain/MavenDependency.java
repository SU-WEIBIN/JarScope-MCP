package io.github.javacodeintel.mcp.domain;

import java.util.Objects;

/** A dependency as seen in an effective Maven project model. */
public record MavenDependency(
        MavenCoordinate coordinate,
        String type,
        String scope,
        boolean optional) {
    public MavenDependency {
        Objects.requireNonNull(coordinate, "coordinate");
        type = type == null || type.isBlank() ? "jar" : type.trim();
        scope = scope == null || scope.isBlank() ? "compile" : scope.trim();
    }
}
