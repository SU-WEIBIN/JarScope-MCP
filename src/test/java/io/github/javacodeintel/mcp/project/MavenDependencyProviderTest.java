package io.github.javacodeintel.mcp.project;

import io.github.javacodeintel.mcp.config.ProjectOptions;
import io.github.javacodeintel.mcp.domain.ChangeDetectionMode;
import io.github.javacodeintel.mcp.domain.JarArtifact;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class MavenDependencyProviderTest {
    @Test
    void buildsArtifactsFromClasspathOutputWithoutNetwork() throws Exception {
        Path root = Files.createTempDirectory("maven-project-");
        Files.writeString(root.resolve("pom.xml"), "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><groupId>demo</groupId><artifactId>app</artifactId><version>1</version></project>");
        Path jar = root.resolve("repo/demo/lib/1.0/lib-1.0.jar"); Files.createDirectories(jar.getParent()); Files.write(jar, new byte[]{1});
        MavenProcessRunner runner = pom -> new MavenProcessRunner.Result(0, jar.toString(), "");
        assertThat(new MavenDependencyProvider(runner).resolve(root, ProjectOptions.defaults()).jars()).singleElement().extracting(JarArtifact::path).isEqualTo(jar.toAbsolutePath());
    }

    @Test
    void passesTestDependencyChoiceToMavenRunner() throws Exception {
        Path root = Files.createTempDirectory("maven-project-");
        Files.writeString(root.resolve("pom.xml"), "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><groupId>demo</groupId><artifactId>app</artifactId><version>1</version></project>");
        Path jar = root.resolve("repo/demo/lib/1.0/lib-1.0.jar");
        Files.createDirectories(jar.getParent());
        Files.write(jar, new byte[]{1});
        AtomicBoolean includeTestDependencies = new AtomicBoolean();
        MavenProcessRunner runner = new MavenProcessRunner() {
            @Override
            public Result run(Path pom) {
                return new Result(0, jar.toString(), "");
            }

            @Override
            public Result run(Path pom, boolean includeTests) {
                includeTestDependencies.set(includeTests);
                return run(pom);
            }
        };
        ProjectOptions options = new ProjectOptions(8, false, ChangeDetectionMode.NONE, true);

        new MavenDependencyProvider(runner).resolve(root, options);

        assertThat(includeTestDependencies).isTrue();
    }

    @Test
    void resolvesExternalJarsWhenReactorDependencyHasNoInstalledArtifact() throws Exception {
        Path root = Files.createTempDirectory("maven-reactor-");
        Path module = root.resolve("module");
        Files.createDirectories(module);
        Path rootPom = root.resolve("pom.xml");
        Path modulePom = module.resolve("pom.xml");
        Files.writeString(rootPom, "<project><modules><module>module</module></modules></project>");
        Files.writeString(modulePom, "<project/>");
        Path externalJar = root.resolve("repo/external-1.jar");
        Files.createDirectories(externalJar.getParent());
        Files.write(externalJar, new byte[]{1});
        String effective = """
                <projects>
                  <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>demo</groupId><artifactId>root</artifactId><version>1</version>
                    <packaging>pom</packaging><build><directory>%s</directory></build>
                  </project>
                  <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>demo</groupId><artifactId>module</artifactId><version>1</version>
                    <build><directory>%s</directory></build>
                    <dependencies><dependency>
                      <groupId>demo</groupId><artifactId>root</artifactId><version>1</version>
                    </dependency><dependency>
                      <groupId>external</groupId><artifactId>library</artifactId><version>1</version>
                    </dependency></dependencies>
                  </project>
                </projects>
                """.formatted(root.resolve("target"), module.resolve("target"));
        AtomicBoolean sawProbeWithoutWorkspace = new AtomicBoolean();
        MavenProcessRunner runner = new MavenProcessRunner() {
            @Override
            public Result run(Path pom) throws java.io.IOException {
                String probe = Files.readString(pom);
                if (!probe.contains("<artifactId>root</artifactId>")) {
                    sawProbeWithoutWorkspace.set(true);
                }
                return new Result(0, externalJar.toString(), "");
            }

            @Override
            public Result effectivePom(Path pom) {
                return new Result(0, effective, "");
            }
        };

        var resolution = new MavenDependencyProvider(runner).resolve(root, ProjectOptions.defaults());

        assertThat(resolution.reactor().modules()).hasSize(2);
        assertThat(resolution.jars()).extracting(JarArtifact::path).containsExactly(externalJar.toAbsolutePath());
        assertThat(sawProbeWithoutWorkspace).isTrue();
    }

    @Test
    void keepsClasspathOutputFromARecoverableMavenFailureAsWarning() throws Exception {
        Path root = Files.createTempDirectory("maven-partial-");
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        Path jar = root.resolve("repo/partial.jar");
        Files.createDirectories(jar.getParent());
        Files.write(jar, new byte[]{1});
        MavenProcessRunner runner = new MavenProcessRunner() {
            @Override
            public Result run(Path pom) {
                return new Result(1, jar.toString(), "unresolved workspace artifact");
            }
        };

        var resolution = new MavenDependencyProvider(runner).resolve(root, ProjectOptions.defaults());

        assertThat(resolution.jars()).extracting(JarArtifact::path).containsExactly(jar.toAbsolutePath());
        assertThat(resolution.diagnostics()).anyMatch(d -> "MAVEN_PARTIAL_RESOLUTION".equals(d.code()));
    }
}
