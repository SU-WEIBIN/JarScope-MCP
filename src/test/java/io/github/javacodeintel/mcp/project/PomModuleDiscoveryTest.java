package io.github.javacodeintel.mcp.project;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PomModuleDiscoveryTest {
    @Test
    void discoversModulesInPomOrderAndRejectsEscapes() throws Exception {
        Path root = Files.createTempDirectory("maven-project-");
        Files.writeString(root.resolve("pom.xml"), "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><modules><module>module-a</module><module>module-b</module></modules></project>");
        Files.createDirectories(root.resolve("module-a")); Files.createDirectories(root.resolve("module-b"));
        Files.writeString(root.resolve("module-a/pom.xml"), "<project/>"); Files.writeString(root.resolve("module-b/pom.xml"), "<project/>");
        assertThat(new PomModuleDiscovery().discover(root.resolve("pom.xml"))).containsExactly(root.resolve("module-a/pom.xml").toAbsolutePath(), root.resolve("module-b/pom.xml").toAbsolutePath());
    }
}
