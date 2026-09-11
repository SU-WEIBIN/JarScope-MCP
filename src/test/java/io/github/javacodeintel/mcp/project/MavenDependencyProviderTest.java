package io.github.javacodeintel.mcp.project;

import io.github.javacodeintel.mcp.config.ProjectOptions;
import io.github.javacodeintel.mcp.domain.JarArtifact;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MavenDependencyProviderTest {
    @Test
    void buildsArtifactsFromClasspathOutputWithoutNetwork() throws Exception {
        Path root = Files.createTempDirectory("maven-project-");
        Files.writeString(root.resolve("pom.xml"), "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><groupId>demo</groupId><artifactId>app</artifactId><version>1</version></project>");
        Path jar = root.resolve("repo/demo/lib/1.0/lib-1.0.jar"); Files.createDirectories(jar.getParent()); Files.write(jar, new byte[]{1});
        MavenProcessRunner runner = pom -> new MavenProcessRunner.Result(0, jar.toString(), "");
        assertThat(new MavenDependencyProvider(runner).resolve(root, ProjectOptions.defaults())).singleElement().extracting(JarArtifact::path).isEqualTo(jar.toAbsolutePath());
    }
}
