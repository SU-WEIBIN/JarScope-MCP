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
}
