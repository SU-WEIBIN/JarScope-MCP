package io.github.javacodeintel.mcp.config;

import io.github.javacodeintel.mcp.domain.ChangeDetectionMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectOptionsTest {
    @Test
    void defaultsTargetJavaEightWithoutJarHashing() {
        ProjectOptions options = ProjectOptions.defaults();

        assertThat(options.targetJavaRelease()).isEqualTo(8);
        assertThat(options.enforceTargetRelease()).isFalse();
        assertThat(options.changeDetection()).isEqualTo(ChangeDetectionMode.NONE);
        assertThat(options.includeTestDependencies()).isFalse();
    }
}
