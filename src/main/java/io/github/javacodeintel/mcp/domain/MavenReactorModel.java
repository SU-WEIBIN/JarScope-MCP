package io.github.javacodeintel.mcp.domain;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Maven projects imported together as one workspace reactor. */
public record MavenReactorModel(Path rootPom, List<MavenModuleModel> modules) {
    public MavenReactorModel {
        rootPom = Objects.requireNonNull(rootPom, "rootPom").toAbsolutePath().normalize();
        modules = modules == null ? List.of() : List.copyOf(modules);
    }

    public boolean isWorkspaceDependency(MavenDependency dependency) {
        MavenCoordinate candidate = dependency.coordinate();
        if (blankToNull(candidate.groupId()) == null
                || blankToNull(candidate.artifactId()) == null
                || blankToNull(candidate.version()) == null) {
            return false;
        }
        boolean workspaceTestJar = "test-jar".equals(dependency.type())
                && "tests".equalsIgnoreCase(blankToNull(candidate.classifier()));
        if (candidate.classifier() != null && !candidate.classifier().isBlank() && !workspaceTestJar) {
            return false;
        }
        for (MavenModuleModel module : modules) {
            MavenCoordinate workspace = module.coordinate();
            if (same(workspace.groupId(), candidate.groupId())
                    && same(workspace.artifactId(), candidate.artifactId())
                    && same(workspace.version(), candidate.version())
                    && compatibleType(module.packaging(), dependency.type())) {
                return true;
            }
        }
        return false;
    }

    private static boolean compatibleType(String packaging, String type) {
        return Objects.equals(packaging, type)
                || (Objects.equals(packaging, "jar") && (Objects.equals(type, "jar") || Objects.equals(type, "test-jar")));
    }

    private static boolean same(String left, String right) {
        return Objects.equals(blankToNull(left), blankToNull(right));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
