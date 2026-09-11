package io.github.javacodeintel.mcp.project;

import io.github.javacodeintel.mcp.domain.ChangeDetectionMode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JarChangeDetectorTest {
    @Test
    void noneDoesNotReadContentMetadataComparesAndSha256ReadsOnlyWhenSelected() throws Exception {
        Path jar = Files.createTempFile("jci-change-", ".jar"); Files.writeString(jar, "one");
        JarChangeDetector detector = new JarChangeDetector();
        JarChangeDetector.Snapshot snapshot = detector.snapshot(jar, ChangeDetectionMode.METADATA);
        assertThat(detector.detect(jar, ChangeDetectionMode.NONE, snapshot).changed()).isFalse();
        assertThat(detector.detect(jar, ChangeDetectionMode.METADATA, snapshot).changed()).isFalse();
        Files.writeString(jar, "two");
        assertThat(detector.detect(jar, ChangeDetectionMode.METADATA, snapshot).changed()).isTrue();
        JarChangeDetector.Snapshot digest = detector.snapshot(jar, ChangeDetectionMode.SHA256);
        assertThat(detector.detect(jar, ChangeDetectionMode.SHA256, digest).changed()).isFalse();
    }
}
