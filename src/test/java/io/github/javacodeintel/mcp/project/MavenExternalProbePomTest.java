package io.github.javacodeintel.mcp.project;

import io.github.javacodeintel.mcp.domain.MavenCoordinate;
import io.github.javacodeintel.mcp.domain.MavenDependency;
import io.github.javacodeintel.mcp.domain.MavenModuleModel;
import io.github.javacodeintel.mcp.domain.MavenReactorModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MavenExternalProbePomTest {
    @Test
    void removesWorkspaceAndTestDependenciesButPreservesExternalResolutionData() throws Exception {
        Path root = Files.createTempDirectory("probe-pom-");
        Path modulePom = root.resolve("app/pom.xml");
        Files.createDirectories(modulePom.getParent());
        Files.writeString(modulePom, "<project/>");
        String effective = """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <groupId>demo</groupId><artifactId>app</artifactId><version>1</version>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>managed</groupId><artifactId>bom</artifactId><version>3</version></dependency>
                  </dependencies></dependencyManagement>
                  <repositories><repository><id>private</id><url>https://repo.invalid/maven</url></repository></repositories>
                  <dependencies>
                    <dependency><groupId>demo</groupId><artifactId>workspace-lib</artifactId><version>1</version></dependency>
                    <dependency><groupId>external</groupId><artifactId>api</artifactId><version>2</version><scope>provided</scope><optional>true</optional><classifier>client</classifier>
                      <exclusions><exclusion><groupId>bad</groupId><artifactId>transitive</artifactId></exclusion></exclusions>
                    </dependency>
                    <dependency><groupId>test</groupId><artifactId>fixture</artifactId><version>4</version><scope>test</scope></dependency>
                  </dependencies>
                </project>
                """;
        MavenModuleModel module = new MavenModuleModel(
                modulePom, modulePom.getParent(), new MavenCoordinate("demo", "app", "1"),
                "jar", null, null, null, null, 8,
                List.of(
                        new MavenDependency(new MavenCoordinate("demo", "workspace-lib", "1"), "jar", "compile", false),
                        new MavenDependency(new MavenCoordinate("external", "api", "2", "client"), "jar", "provided", true),
                        new MavenDependency(new MavenCoordinate("test", "fixture", "4"), "jar", "test", false)),
                effective);
        MavenReactorModel reactor = new MavenReactorModel(root.resolve("pom.xml"), List.of(
                new MavenModuleModel(root.resolve("workspace/pom.xml"), root.resolve("workspace"),
                        new MavenCoordinate("demo", "workspace-lib", "1"), "jar", null, null, null, null, 8, List.of(), "<project/>"),
                module));

        String probe = MavenExternalProbePom.render(module, reactor, false);

        assertThat(probe).contains("<artifactId>api</artifactId>", "<scope>provided</scope>", "<classifier>client</classifier>")
                .contains("<artifactId>bom</artifactId>", "https://repo.invalid/maven")
                .contains("<artifactId>transitive</artifactId>");
        assertThat(probe).doesNotContain("<artifactId>workspace-lib</artifactId>", "<artifactId>fixture</artifactId>");
    }
}
