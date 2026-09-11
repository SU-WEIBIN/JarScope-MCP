package io.github.javacodeintel.mcp.config;
import io.github.javacodeintel.mcp.domain.ChangeDetectionMode;
import java.util.Objects;
public record ProjectOptions(int targetJavaRelease, boolean enforceTargetRelease, ChangeDetectionMode changeDetection, boolean includeTestDependencies) {
 public ProjectOptions { if (targetJavaRelease < 8 || targetJavaRelease > Runtime.version().feature()) throw new IllegalArgumentException("targetJavaRelease must be between 8 and " + Runtime.version().feature()); Objects.requireNonNull(changeDetection, "changeDetection"); }
 public static ProjectOptions defaults() { return new ProjectOptions(8, false, ChangeDetectionMode.NONE, false); }
}
