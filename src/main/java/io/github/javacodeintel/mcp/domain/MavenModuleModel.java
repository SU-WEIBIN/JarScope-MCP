package io.github.javacodeintel.mcp.domain;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** The subset of an effective Maven model needed by dependency indexing. */
public record MavenModuleModel(
        Path pomPath,
        Path baseDirectory,
        MavenCoordinate coordinate,
        String packaging,
        Path sourceDirectory,
        Path testSourceDirectory,
        Path outputDirectory,
        Path testOutputDirectory,
        int targetJavaRelease,
        List<MavenDependency> dependencies,
        String effectiveProjectXml) {
    public MavenModuleModel {
        pomPath = Objects.requireNonNull(pomPath, "pomPath").toAbsolutePath().normalize();
        baseDirectory = Objects.requireNonNull(baseDirectory, "baseDirectory").toAbsolutePath().normalize();
        Objects.requireNonNull(coordinate, "coordinate");
        packaging = packaging == null || packaging.isBlank() ? "jar" : packaging.trim();
        sourceDirectory = normalize(sourceDirectory);
        testSourceDirectory = normalize(testSourceDirectory);
        outputDirectory = normalize(outputDirectory);
        testOutputDirectory = normalize(testOutputDirectory);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        effectiveProjectXml = Objects.requireNonNull(effectiveProjectXml, "effectiveProjectXml");
    }

    private static Path normalize(Path path) {
        return path == null ? null : path.toAbsolutePath().normalize();
    }
}
