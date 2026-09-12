package io.github.javacodeintel.mcp.index;

import io.github.javacodeintel.mcp.config.ProjectOptions;
import io.github.javacodeintel.mcp.domain.*;
import io.github.javacodeintel.mcp.testing.JavaFixtureCompiler;
import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class JarClassScannerTest {
    @Test
    void scansClassEntriesAndReportsInvalidEntries() throws Exception {
        Path root = Files.createTempDirectory("jci-jar-");
        byte[] bytes = JavaFixtureCompiler.compileBytes(root, "demo.One", "package demo; public class One { }");
        Path jarPath = root.resolve("fixture.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jarPath))) {
            out.putNextEntry(new JarEntry("demo/One.class")); out.write(bytes); out.closeEntry();
            out.putNextEntry(new JarEntry("broken.class")); out.write(new byte[]{1, 2, 3}); out.closeEntry();
        }
        JarArtifact jar = new JarArtifact(jarPath, new MavenCoordinate("demo", "fixture", "1"));
        ScanResult result = new JarClassScanner(new AsmBytecodeParser()).scan(jar, ProjectOptions.defaults());
        assertThat(result.classes()).extracting(pc -> pc.classInfo().name()).containsExactly("demo.One");
        assertThat(result.diagnostics()).anyMatch(d -> d.jarEntry().equals("broken.class"));
    }

    @Test
    void excludesClassFilesAboveTargetWhenEnforced() throws Exception {
        Path root = Files.createTempDirectory("jci-jar-version-");
        byte[] bytes = JavaFixtureCompiler.compileBytes(root, "demo.Newer", "package demo; public class Newer { }");
        // Java 21 uses class-file major version 65; keep the otherwise valid fixture
        // so the scanner can exercise the target-version gate before ASM parsing.
        bytes[6] = 0;
        bytes[7] = 65;
        Path jarPath = root.resolve("newer.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jarPath))) {
            out.putNextEntry(new JarEntry("demo/Newer.class"));
            out.write(bytes);
            out.closeEntry();
        }

        JarArtifact jar = new JarArtifact(jarPath, new MavenCoordinate("demo", "newer", "1"));
        ProjectOptions options = new ProjectOptions(8, true, ChangeDetectionMode.NONE, false);
        ScanResult result = new JarClassScanner(new AsmBytecodeParser()).scan(jar, options);

        assertThat(result.classes()).isEmpty();
        assertThat(result.diagnostics()).anySatisfy(d -> {
            assertThat(d.code()).isEqualTo("CLASS_VERSION_EXCLUDED");
            assertThat(d.jarEntry()).isEqualTo("demo/Newer.class");
        });

        ScanResult permissive = new JarClassScanner(new AsmBytecodeParser()).scan(
                jar, new ProjectOptions(8, false, ChangeDetectionMode.NONE, false));
        assertThat(permissive.classes()).singleElement()
                .extracting(pc -> pc.classInfo().classFileMajorVersion())
                .isEqualTo(65);
    }
}
