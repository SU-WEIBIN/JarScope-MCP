package io.github.javacodeintel.mcp.project;

import io.github.javacodeintel.mcp.domain.MavenModuleModel;
import io.github.javacodeintel.mcp.domain.MavenReactorModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MavenEffectivePomParserTest {
    @Test
    void mapsAggregatedEffectiveProjectsToWorkspaceModules() throws Exception {
        Path root = Files.createTempDirectory("effective-reactor-").toAbsolutePath();
        Path child = root.resolve("child");
        Files.createDirectories(child);
        Path rootPom = root.resolve("pom.xml");
        Path childPom = child.resolve("pom.xml");
        Files.writeString(rootPom, "<project/>");
        Files.writeString(childPom, "<project/>");
        String xml = """
                <projects>
                  <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>demo</groupId><artifactId>root</artifactId><version>1</version>
                    <packaging>jar</packaging>
                    <properties><maven.compiler.release>8</maven.compiler.release></properties>
                    <build><directory>%s</directory><sourceDirectory>%s</sourceDirectory></build>
                  </project>
                  <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>demo</groupId><artifactId>child</artifactId><version>1</version>
                    <build><directory>%s</directory><sourceDirectory>%s</sourceDirectory></build>
                    <dependencies>
                      <dependency><groupId>demo</groupId><artifactId>root</artifactId><version>1</version></dependency>
                      <dependency><groupId>external</groupId><artifactId>library</artifactId><version>2</version><scope>provided</scope><optional>true</optional></dependency>
                    </dependencies>
                  </project>
                </projects>
                """.formatted(
                xml(root.resolve("target")), xml(root.resolve("src/main/java")),
                xml(child.resolve("target")), xml(child.resolve("src/main/java")));

        MavenReactorModel model = new MavenEffectivePomParser().parse(
                xml, rootPom, List.of(childPom));

        assertThat(model.modules()).extracting(module -> module.coordinate().artifactId())
                .containsExactly("root", "child");
        MavenModuleModel childModel = model.modules().get(1);
        assertThat(childModel.pomPath()).isEqualTo(childPom);
        assertThat(childModel.sourceDirectory()).isEqualTo(child.resolve("src/main/java"));
        assertThat(childModel.targetJavaRelease()).isEqualTo(8);
        assertThat(childModel.dependencies()).hasSize(2);
        assertThat(model.isWorkspaceDependency(childModel.dependencies().get(0))).isTrue();
        assertThat(model.isWorkspaceDependency(childModel.dependencies().get(1))).isFalse();
        assertThat(childModel.dependencies().get(1).scope()).isEqualTo("provided");
        assertThat(childModel.dependencies().get(1).optional()).isTrue();
    }

    @Test
    void fallsBackWhenEffectivePomContainsMalformedUncDirectory() throws Exception {
        Path root = Files.createTempDirectory("effective-unc-").toAbsolutePath();
        Path rootPom = root.resolve("pom.xml");
        Files.writeString(rootPom, "<project/>");
        String xml = """
                <projects><project xmlns="http://maven.apache.org/POM/4.0.0">
                  <groupId>demo</groupId><artifactId>broken</artifactId><version>1</version>
                  <build><sourceDirectory>//120.46.143.122</sourceDirectory></build>
                </project></projects>
                """;

        MavenModuleModel module = new MavenEffectivePomParser().parse(xml, rootPom, List.of()).modules().get(0);

        assertThat(module.sourceDirectory()).isEqualTo(root.resolve("src/main/java"));
    }

    private static String xml(Path path) {
        return path.toString().replace("&", "&amp;");
    }
}
